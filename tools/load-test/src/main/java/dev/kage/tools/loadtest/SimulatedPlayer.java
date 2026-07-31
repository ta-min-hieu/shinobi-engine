package dev.kage.tools.loadtest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * 1 người chơi giả lập — nói chuyện qua ĐÚNG giao thức dây (socket TCP thuần + protobuf, giống hệt
 * {@code GatewayServerIntegrationTest}), KHÔNG gọi tắt API nội bộ server. Vòng lặp hành vi
 * (BENCHMARK.md §5 "giả lập hành vi thật — move + combat + chat"): mỗi hành động là 1 command đi
 * kèm phản hồi đọc thật (đo latency) hoặc fire-and-forget (SetTargetCommand — auto-attack, GDD §6).
 *
 * <p>Chỉ dummy tập luyện cố định (5,5) tồn tại trong World chính lúc viết lát cắt này — mọi người
 * chơi giả lập hội tụ về cùng 1 điểm, MÔ PHỎNG ĐÚNG kịch bản áp lực AoI/combat cao nhất hiện có
 * (không phải hạn chế của tool, phản ánh đúng nội dung game hiện tại).
 */
final class SimulatedPlayer implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPlayer.class);
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final float DUMMY_DIR_X = 0.7071f;
    private static final float DUMMY_DIR_Y = 0.7071f;
    // Sau N lỗi LIÊN TIẾP (socket chết hẳn — RateLimiter.shouldDisconnect ngắt kết nối, hoặc mạng
    // đứt thật), client "bỏ cuộc" thay vì spin vô thời hạn — PHÁT HIỆN THẬT (soak test 2026-07-26):
    // không có ngưỡng này, 1 client bị server ngắt vì spam chat sẽ tiếp tục thử+lỗi suốt phần đời
    // còn lại của soak test, làm lỗi cộng dồn tăng dần đều theo thời gian dù server hoàn toàn khoẻ.
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    // World chat giới hạn 5 lần/10s (GatewayChannelHandler.chatWorldRateLimiter) — xác suất này
    // phải đủ thấp để KHÔNG chạm ngưỡng đó dù cộng dồn nhiều cycle liên tiếp (trước là 20, gây
    // client bị ngắt kết nối hàng loạt trong soak test đầu).
    private static final int CHAT_PROBABILITY_PERCENT = 3;

    // ADR-027 — CÙNG cert dev/test dùng ở server-gateway/client-core (xem comment tương ứng ở đó,
    // KHÔNG an toàn cho môi trường thật). Load-test nói ĐÚNG giao thức dây thật kể cả TLS — không
    // gọi tắt, khớp mục đích tool (javadoc class "KHÔNG gọi tắt API nội bộ server").
    private static final String PINNED_CERT_RESOURCE = "tls/dev-cert.pem";
    private static final SSLSocketFactory SSL_SOCKET_FACTORY = buildPinnedSocketFactory();

    /**
     * Gộp mọi bộ đếm chia sẻ giữa các client — Checkstyle ParameterNumber tối đa 4 tham số.
     * {@code bytesSent}/{@code bytesReceived} đo THẬT qua chính socket (gồm 4-byte length prefix +
     * header + payload protobuf) — xấp xỉ đúng traffic cổng 7777, tách biệt hoàn toàn traffic
     * Postgres/Redis (giao thức game và giao thức DB không đi qua cùng 1 socket).
     */
    record Metrics(
            LatencyRecorder latencyRecorder, AtomicLong errorCount, AtomicLong bytesSent, AtomicLong bytesReceived) {}

    private final String host;
    private final int port;
    private final String username;
    private final Metrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(true);

    private Socket socket;
    private int nextSequenceId = 1;

    SimulatedPlayer(String host, int port, String username, Metrics metrics) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.metrics = metrics;
    }

    void stop() {
        running.set(false);
        closeQuietly();
    }

    @Override
    public void run() {
        try (SSLSocket clientSocket = (SSLSocket) SSL_SOCKET_FACTORY.createSocket(host, port)) {
            clientSocket.setSoTimeout(SOCKET_TIMEOUT_MS);
            clientSocket.startHandshake();
            this.socket = clientSocket;
            DataInputStream in = new DataInputStream(clientSocket.getInputStream());

            connectCreateSelect(in);
            long monsterId = findTrainingDummy(in);

            int consecutiveFailures = 0;
            while (running.get() && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
                consecutiveFailures = playOneCycle(in, monsterId) ? 0 : consecutiveFailures + 1;
            }
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                LOG.debug(
                        "Client {} bỏ cuộc sau {} lỗi liên tiếp — kết nối coi như đã chết",
                        username,
                        consecutiveFailures);
            }
        } catch (IOException e) {
            if (running.get()) { // đóng chủ động lúc test kết thúc không tính là lỗi thật.
                metrics.errorCount().incrementAndGet();
                LOG.debug("Client {} lỗi kết nối", username, e);
            }
        }
    }

    private void connectCreateSelect(DataInputStream in) throws IOException {
        ConnectRequest connectRequest =
                ConnectRequest.newBuilder().setUsername(username).build();
        sendFrame(Opcode.OPCODE_CONNECT_REQUEST_VALUE, connectRequest.toByteArray());
        ConnectResponse.parseFrom(readFrame(in).payload());

        CreateCharacterRequest createRequest = CreateCharacterRequest.newBuilder()
                .setName("Load" + UUID.randomUUID().toString().replace("-", "").substring(0, 8))
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        sendFrame(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, createRequest.toByteArray());
        CreateCharacterResponse createResponse =
                CreateCharacterResponse.parseFrom(readFrame(in).payload());

        SelectCharacterRequest selectRequest = SelectCharacterRequest.newBuilder()
                .setCharacterId(createResponse.getCharacter().getId())
                .build();
        sendFrame(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, selectRequest.toByteArray());
        readFrame(in); // SelectCharacterResponse — nội dung không quan trọng ở đây.
    }

    /** Best-effort — dummy tập luyện luôn trong AoI ngay sau spawn (server-world WorldFacade), vài frame đầu là đủ. */
    private long findTrainingDummy(DataInputStream in) {
        try {
            for (int attempt = 0; attempt < 20; attempt++) {
                PacketCodec.Frame frame = readFrame(in);
                if (frame.header().opcode() == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
                    return MonsterSpawnedEvent.parseFrom(frame.payload()).getMonsterId();
                }
            }
        } catch (IOException e) {
            LOG.debug("Client {} không thấy dummy tập luyện trong giới hạn frame — bỏ qua combat action", username, e);
        }
        return 0L; // 0 = không có target hợp lệ — playOneCycle bỏ qua nhánh combat.
    }

    /**
     * PHÁT HIỆN THẬT (soak test đầu tiên, 2026-07-26): {@code Thread.sleep} jitter trước đây nằm
     * CUỐI khối {@code try} — 1 lần {@link IOException}/{@link SocketTimeoutException} (bình
     * thường khi AoI event dồn dập lúc nhiều client tụ 1 điểm) khiến vòng lặp
     * {@link #run} gọi lại {@link #playOneCycle} NGAY LẬP TỨC, không throttle, tạo busy-loop thật —
     * 60 client sinh ra >20 TRIỆU lỗi trong < 5 phút (verify bằng server.log: server hoàn toàn khoẻ
     * mạnh, không tick overrun/exception nào trong cùng khoảng thời gian — lỗi 100% phía client).
     * Sửa: sleep chuyển vào {@code finally} — LUÔN throttle bất kể thành công hay lỗi.
     *
     * @return {@code true} nếu cả cycle chạy trót lọt — {@link #run} dùng giá trị này đếm lỗi LIÊN
     *     TIẾP, bỏ cuộc sau {@link #MAX_CONSECUTIVE_FAILURES} lần (kết nối đã chết hẳn, vd. bị
     *     {@code RateLimiter.shouldDisconnect} ngắt — không có lý do thử lại vô thời hạn).
     */
    private boolean playOneCycle(DataInputStream in, long monsterId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            runCycleBody(in, monsterId, random);
            return true;
        } catch (SocketTimeoutException e) {
            // server chậm/quá tải, hoặc AoI event dồn dập che mất response cần tìm — 1 tín hiệu, không phải crash.
            metrics.errorCount().incrementAndGet();
            return false;
        } catch (IOException e) {
            if (running.get()) {
                metrics.errorCount().incrementAndGet();
            }
            return false;
        } finally {
            sleepJitter(random);
        }
    }

    /** Tách khỏi {@link #playOneCycle} để giữ dưới giới hạn Checkstyle MethodLength. */
    private void runCycleBody(DataInputStream in, long monsterId, ThreadLocalRandom random) throws IOException {
        timedRoundTrip(
                in,
                Opcode.OPCODE_MOVE_COMMAND_VALUE,
                Opcode.OPCODE_MOVE_RESPONSE_VALUE,
                MoveCommand.newBuilder()
                        .setDirX(DUMMY_DIR_X)
                        .setDirY(DUMMY_DIR_Y)
                        .build()
                        .toByteArray());

        if (monsterId != 0 && random.nextInt(100) < 70) { // GDD §6 auto-target — fire-and-forget.
            SetTargetCommand setTarget =
                    SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            sendFrame(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, setTarget.toByteArray());
        }
        if (random.nextInt(100) < CHAT_PROBABILITY_PERCENT) {
            timedRoundTrip(
                    in,
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                    SendChatMessageCommand.newBuilder()
                            .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                            .setBody("load-test " + username)
                            .build()
                            .toByteArray());
        }
        if (random.nextInt(100) < 30) {
            PingCommand ping = PingCommand.newBuilder()
                    .setClientTimestampMs(System.currentTimeMillis())
                    .build();
            timedRoundTrip(in, Opcode.OPCODE_PING_VALUE, Opcode.OPCODE_PONG_VALUE, ping.toByteArray());
        }

        MoveCommand stop = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
        sendFrame(Opcode.OPCODE_MOVE_COMMAND_VALUE, stop.toByteArray());
    }

    private void sleepJitter(ThreadLocalRandom random) {
        try {
            Thread.sleep(300 + random.nextInt(500)); // jitter — tránh mọi client đồng bộ tuyệt đối.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private void timedRoundTrip(DataInputStream in, int commandOpcode, int responseOpcode, byte[] payload)
            throws IOException {
        long startNanos = System.nanoTime();
        sendFrame(commandOpcode, payload);
        readUntilOpcode(in, responseOpcode);
        metrics.latencyRecorder().record(System.nanoTime() - startNanos);
    }

    private void sendFrame(int opcode, byte[] payload) throws IOException {
        DataOutputStream out = new DataOutputStream(socket.getOutputStream());
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, nextSequenceId++);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        out.writeInt(frame.length);
        out.write(frame);
        out.flush();
        metrics.bytesSent().addAndGet(4L + frame.length); // 4 = length prefix (băng thông thật qua cổng 7777).
    }

    // 2000 (không phải 200) — nhiều client tụ 1 điểm (findTrainingDummy javadoc) sinh AoI event
    // fan-out O(n) thật, ngân sách quá nhỏ biến "chậm nhưng vẫn tới" thành "lỗi" giả — soak test
    // đầu tiên (2026-07-26) cho thấy server hoàn toàn khoẻ mạnh trong lúc client báo lỗi hàng loạt.
    private static final int MAX_FRAMES_TO_SKIP = 2000;

    /** AoI Event có thể xen giữa bất kỳ lúc nào (giống GatewayServerIntegrationTest) — bỏ qua tới đúng opcode cần. */
    private PacketCodec.Frame readUntilOpcode(DataInputStream in, int expectedOpcode) throws IOException {
        for (int attempt = 0; attempt < MAX_FRAMES_TO_SKIP; attempt++) {
            PacketCodec.Frame frame = readFrame(in);
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
        }
        throw new IOException("Không thấy opcode " + expectedOpcode + " sau " + MAX_FRAMES_TO_SKIP
                + " frame — server có quá tải không?");
    }

    private PacketCodec.Frame readFrame(DataInputStream in) throws IOException {
        int length = in.readInt();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        // Đếm MỌI frame nhận được (kể cả AoI event bị readUntilOpcode bỏ qua) — băng thông nhận thật.
        metrics.bytesReceived().addAndGet(4L + length);
        return PacketCodec.decodeFrame(bytes);
    }

    private void closeQuietly() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            LOG.debug("Lỗi đóng socket client {} — bỏ qua, đang shutdown", username, e);
        }
    }

    /** Xây 1 lần lúc load class — {@link SSLSocketFactory} PIN đúng cert (ADR-027). */
    private static SSLSocketFactory buildPinnedSocketFactory() {
        try {
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
            Certificate pinnedCert;
            try (InputStream in = SimulatedPlayer.class.getClassLoader().getResourceAsStream(PINNED_CERT_RESOURCE)) {
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
            throw new IllegalStateException("Không load được cert pin (" + PINNED_CERT_RESOURCE + ") — ADR-027", e);
        }
    }
}
