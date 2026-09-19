package dev.kage.client.core.net;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * Kết nối tới server-gateway qua {@link SSLSocket} THUẦN JDK — KHÔNG Netty (Điều 23: chỉ
 * server-gateway được import Netty; TLS 2 phía dùng 2 thư viện khác nhau nhưng vẫn bắt tay được
 * bình thường, TLS là giao thức chuẩn ở tầng dây — ADR-027). Network I/O chạy thread riêng, không
 * block render thread (CLIENT.md §8).
 *
 * <p><b>TLS (ADR-027)</b>: {@link TrustManagerFactory} PIN đúng 1 cert bundled trong resource
 * {@value #PINNED_CERT_RESOURCE} — KHÔNG dùng truststore hệ điều hành (cert self-signed vốn dĩ
 * fail validate mặc định). Hostname verification KHÔNG bật tường minh (mặc định JDK {@link
 * SSLSocket} đã tắt sẵn, không cần code gì thêm) — pin đúng cert/public key là điều kiện MẠNH HƠN
 * hostname+CA chain cho 1 cặp client-server đóng, miễn pin đúng full cert (đã làm ở đây), không
 * phải chỉ tắt verify rồi không bù lại bằng gì.
 *
 * <p>Packet nhận về đưa vào queue; caller (KageEngine ở đầu tick) tự {@link #pollInbound()} —
 * không xử lý packet giữa lúc render.
 *
 * <p>Mất kết nối tự động thử lại theo backoff tăng dần (NETWORK.md §10) — chống thundering herd
 * khi nhiều client cùng mất kết nối lúc server phục hồi.
 */
public final class GameConnection {

    private static final Logger LOG = LoggerFactory.getLogger(GameConnection.class);

    private static final int[] RECONNECT_BACKOFF_SECONDS = {1, 2, 4, 8, 16, 30};
    private static final long HEARTBEAT_INTERVAL_MS = 15_000;
    // Chỉ áp dụng lúc bắt tay TLS — tránh treo vô thời hạn nếu mạng "đen" (black-hole) hoặc phía
    // kia không phản hồi ClientHello. Đọc dữ liệu game SAU handshake vẫn chờ vô thời hạn như cũ
    // (readLoop() dựa vào readInt() blocking — hành vi có từ trước TLS, ngoài phạm vi ADR-027).
    private static final int TLS_HANDSHAKE_TIMEOUT_MS = 10_000;

    /**
     * Trần khung ĐỌC VÀO — cố ý khớp {@code GatewayServer.MAX_FRAME_LENGTH} (1MB).
     *
     * <p>Phía server đã chặn khung khổng lồ từ lâu ({@code LengthFieldBasedFrameDecoder}, có hẳn ô
     * test bảo mật cho nó), còn client thì {@code new byte[in.readInt()]} theo đúng con số trên dây
     * — một bất đối xứng, và nó hỏng theo BA kiểu:
     *
     * <ul>
     *   <li>số quá lớn ⇒ {@code OutOfMemoryError} ngay lập tức;
     *   <li>số ÂM ⇒ {@code NegativeArraySizeException}, mà {@code connectionLoop} chỉ bắt
     *       {@code IOException} ⇒ luồng kết nối CHẾT HẲN, không bao giờ thử lại nữa;
     *   <li>số lớn vừa phải nhưng bịa ⇒ {@code readFully} chờ mãi (sau bắt tay TLS
     *       {@code soTimeout} đã về 0) ⇒ client treo im, cũng không thử lại.
     * </ul>
     *
     * <p>Không cần một server thù địch mới chạm tới: một khung dở dang là đủ.
     */
    private static final int MAX_FRAME_LENGTH = 1024 * 1024;

    /** Header 7 byte (NETWORK.md §102) — khung ngắn hơn thế thì chắc chắn rác. */
    private static final int MIN_FRAME_LENGTH = 7;

    // ADR-027 — cert self-signed DEV/TEST mặc định, dùng chung với server/server-gateway test
    // resource. ⚠️ KHÔNG an toàn cho Closed Beta/Production thật (private key tương ứng đã public
    // trên GitHub) — operator PHẢI tự tạo cặp cert/key riêng rồi thay file resource này + build lại
    // client trước khi phát hành cho người chơi thật (xem ADR-027 "Cấp chứng chỉ").
    private static final String PINNED_CERT_RESOURCE = "tls/dev-cert.pem";
    private static final SSLSocketFactory SSL_SOCKET_FACTORY = buildPinnedSocketFactory();

    private final String host;
    private final int port;
    private final Queue<PacketCodec.Frame> inbound = new ConcurrentLinkedQueue<>();
    private final AtomicInteger nextSequenceId = new AtomicInteger(1);

    /**
     * Tăng một lần mỗi khi {@code doConnect} dựng xong socket. Luồng heartbeat giữ lại con số của
     * lần kết nối sinh ra nó và tự thoát khi số đã đổi — xem {@link #startHeartbeat()}.
     *
     * <p>Đếm thế hệ chứ không so {@code socket == owner}: PMD {@code CompareObjectsWithEquals} chặn
     * so sánh tham chiếu, mà ở đây ý cần diễn đạt vốn là "còn là LẦN KẾT NỐI ấy không", không phải
     * "còn là đối tượng ấy không".
     */
    private final AtomicInteger connectionGeneration = new AtomicInteger();

    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile Socket socket;
    private volatile DataOutputStream out;
    private Thread connectionThread;
    private Thread heartbeatThread;

    public GameConnection(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        connectionThread = new Thread(this::connectionLoop, "game-connection");
        connectionThread.setDaemon(true);
        connectionThread.start();
    }

    public void stop() {
        running.set(false);
        closeQuietly();
        if (connectionThread != null) {
            connectionThread.interrupt();
        }
        if (heartbeatThread != null) {
            heartbeatThread.interrupt();
        }
    }

    public boolean isConnected() {
        Socket current = socket;
        return current != null && current.isConnected() && !current.isClosed();
    }

    /** Client sinh sequenceId tăng dần THEO KẾT NỐI (NETWORK.md §105). */
    public int nextSequenceId() {
        return nextSequenceId.getAndIncrement();
    }

    public void send(int opcode, int sequenceId, byte[] payload) throws IOException {
        DataOutputStream stream = out;
        if (stream == null) {
            throw new IOException("Chưa kết nối tới server");
        }
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        synchronized (stream) {
            stream.writeInt(frame.length);
            stream.write(frame);
            stream.flush();
        }
    }

    /** Gọi ở ĐẦU TICK — không xử lý packet giữa render (CLIENT.md §8). */
    public PacketCodec.Frame pollInbound() {
        return inbound.poll();
    }

    private void connectionLoop() {
        int attempt = 0;
        while (running.get()) {
            try {
                doConnect();
                attempt = 0; // reset backoff sau khi kết nối thành công
                readLoop(); // block tới khi mất kết nối
            } catch (IOException e) {
                // Mất kết nối (server restart, mạng chập chờn) là chuyện BÌNH THƯỜNG, không phải
                // lỗi nghiêm trọng — nhưng vẫn phải LOG (Điều 42: không nuốt exception im lặng),
                // chỉ ở mức DEBUG vì đây là tình huống dự kiến, không phải bất thường.
                LOG.debug("Mất kết nối tới {}:{}, sẽ thử lại theo backoff", host, port, e);
            }
            closeQuietly();
            if (!running.get()) {
                return;
            }
            int backoffSeconds = RECONNECT_BACKOFF_SECONDS[Math.min(attempt, RECONNECT_BACKOFF_SECONDS.length - 1)];
            attempt++;
            LOG.info("Thử kết nối lại sau {}s (lần {})", backoffSeconds, attempt);
            sleepQuietly(backoffSeconds * 1000L);
        }
    }

    private void doConnect() throws IOException {
        SSLSocket newSocket = (SSLSocket) SSL_SOCKET_FACTORY.createSocket(host, port);
        // Bắt tay TLS NGAY — fail nhanh và rõ ràng (SSLHandshakeException) nếu cert không khớp
        // pin hoặc server không bật TLS, thay vì đợi tới lần I/O đầu tiên mới phát hiện.
        newSocket.setSoTimeout(TLS_HANDSHAKE_TIMEOUT_MS);
        newSocket.startHandshake();
        newSocket.setSoTimeout(0);
        socket = newSocket;
        out = new DataOutputStream(newSocket.getOutputStream());
        connectionGeneration.incrementAndGet();
        LOG.info("Đã kết nối tới {}:{}", host, port);
        startHeartbeat();
    }

    /** Xây 1 lần lúc load class — {@link SSLSocketFactory} PIN đúng cert (ADR-027, xem javadoc class). */
    private static SSLSocketFactory buildPinnedSocketFactory() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate pinnedCert;
            try (InputStream in = GameConnection.class.getClassLoader().getResourceAsStream(PINNED_CERT_RESOURCE)) {
                pinnedCert = certificateFactory.generateCertificate(in);
            }
            KeyStore trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            trustStore.load(null, null);
            trustStore.setCertificateEntry("kage-server", pinnedCert);

            TrustManagerFactory trustManagerFactory =
                    TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            trustManagerFactory.init(trustStore);

            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagerFactory.getTrustManagers(), null);
            return sslContext.getSocketFactory();
        } catch (GeneralSecurityException | IOException e) {
            // Lỗi khởi động thật (cert resource thiếu/hỏng) — client không thể kết nối được dù
            // sao, fail fast lúc load class rõ ràng hơn NPE mơ hồ lúc connect() sau này.
            throw new IllegalStateException("Không load được cert pin (" + PINNED_CERT_RESOURCE + ") — ADR-027", e);
        }
    }

    private void readLoop() throws IOException {
        DataInputStream in = new DataInputStream(socket.getInputStream());
        while (running.get()) {
            int length = in.readInt(); // ném IOException khi mất kết nối — vòng lặp tự thoát
            byte[] frameBytes = new byte[checkedFrameLength(length)];
            in.readFully(frameBytes);
            inbound.add(PacketCodec.decodeFrame(frameBytes));
        }
    }

    /**
     * Cửa duy nhất giữa con số trên dây và {@code new byte[...]} — xem {@link #MAX_FRAME_LENGTH}.
     *
     * <p>Ném {@link IOException} chứ không phải lỗi runtime là CHỦ ĐÍCH: {@code connectionLoop} đã
     * coi {@code IOException} là "mất kết nối bình thường", nên một khung rác chỉ làm client đóng
     * rồi kết nối lại theo backoff — đúng thứ nó đã làm với mọi hỏng hóc dây khác.
     */
    static int checkedFrameLength(int declared) throws IOException {
        if (declared < MIN_FRAME_LENGTH || declared > MAX_FRAME_LENGTH) {
            throw new IOException("Khung có độ dài không hợp lệ: " + declared + " byte (cho phép " + MIN_FRAME_LENGTH
                    + ".." + MAX_FRAME_LENGTH + ")");
        }
        return declared;
    }

    /**
     * ⚠️ Một luồng heartbeat thuộc về ĐÚNG MỘT socket, và {@code doConnect} gọi hàm này sau MỌI lần
     * kết nối lại.
     *
     * <p>Bản trước lặp theo {@code running && isConnected()} và ghi đè {@code heartbeatThread} mà
     * không dừng luồng cũ. Luồng cũ đang nằm trong {@code sleep(15s)} thì không thấy quãng mất kết
     * nối (backoff ngắn nhất là 1 giây): lúc nó tỉnh dậy, {@code isConnected()} đã TRUE trở lại vì
     * socket MỚI — nên nó sống tiếp và ping trên socket của người khác. Mỗi lần mạng chập chờn cộng
     * thêm một luồng vĩnh viễn, và mỗi luồng là một gói nữa mỗi 15 giây — đúng thứ ADR-042 đang
     * muốn bớt. {@code stop()} cũng chỉ với tới được luồng MỚI NHẤT, những luồng kia không ai gọi
     * tên nữa.
     *
     * <p>Sửa bằng cách buộc luồng vào LẦN KẾT NỐI sinh ra nó ({@link #connectionGeneration}): số
     * đổi ⇒ vòng lặp thoát ở vòng kiểm kế tiếp, không cần ai dừng hộ. Luồng cũ vẫn được
     * {@code interrupt()} để nó khỏi ngủ nốt 15 giây vô ích.
     *
     * <p>Không có ô test cho chỗ này: harness của {@code client-core} dùng {@code ServerSocket}
     * thuần nên bắt tay TLS không bao giờ xong, mà {@code startHeartbeat} chỉ chạy SAU bắt tay
     * (xem {@code GameConnectionReconnectTest}). Sửa theo hướng làm cho lỗi KHÔNG THỂ xảy ra về mặt
     * cấu trúc thay vì canh nó bằng một cờ.
     */
    private void startHeartbeat() {
        Thread previous = heartbeatThread;
        if (previous != null) {
            previous.interrupt();
        }
        int generation = connectionGeneration.get();
        heartbeatThread = new Thread(
                () -> {
                    while (running.get() && connectionGeneration.get() == generation && isConnected()) {
                        try {
                            PingCommand ping = PingCommand.newBuilder()
                                    .setClientTimestampMs(System.currentTimeMillis())
                                    .build();
                            send(Opcode.OPCODE_PING_VALUE, nextSequenceId(), ping.toByteArray());
                            Thread.sleep(HEARTBEAT_INTERVAL_MS);
                        } catch (IOException e) {
                            LOG.debug("Gửi heartbeat thất bại, kết nối có thể đã chết", e);
                            return;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            return;
                        }
                    }
                },
                "game-connection-heartbeat");
        heartbeatThread.setDaemon(true);
        heartbeatThread.start();
    }

    private void closeQuietly() {
        try {
            Socket current = socket;
            if (current != null) {
                current.close();
            }
        } catch (IOException ignored) {
            // Đóng kết nối đã chết — không có gì để xử lý thêm, tên biến "ignored" là chủ đích.
        }
        socket = null;
        out = null;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
