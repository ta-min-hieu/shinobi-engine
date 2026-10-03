package dev.kage.tools.loadtest;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.ListCharactersRequest;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;

/**
 * MỘT kết nối tới gateway nói ĐÚNG giao thức dây thật — TLS (cert pin, ADR-027) + 4-byte length
 * prefix + {@link PacketCodec} header + payload protobuf. KHÔNG gọi tắt API nội bộ server.
 *
 * <p>Tách ra khỏi {@link SimulatedPlayer} (2026-09-06) vì công cụ thứ hai cần đúng phần này:
 * {@code probe.GameProbe} — client HEADLESS quan sát, đọc {@code MonsterSpawnedEvent}/
 * {@code AttackResultEvent} thành SỐ (ROADMAP mục 58). Hai công cụ khác nhau ở HÀNH VI (một cái
 * tạo tải, một cái quan sát), không khác gì ở tầng dây — copy-paste tầng dây là cách chắc chắn
 * nhất để hai công cụ nói hai thứ tiếng hơi khác nhau rồi cho ra hai kết luận khác nhau.
 *
 * <p>Không thread-safe: 1 kết nối = 1 luồng điều khiển. {@link #close()} gọi được từ luồng khác
 * (dừng test) — đóng socket làm luồng đang đọc bật ra {@link IOException}, đúng ý.
 */
public final class GameConnection implements AutoCloseable, FrameChannel {

    private static final Logger LOG = LoggerFactory.getLogger(GameConnection.class);

    /** 4 byte length prefix bọc ngoài mỗi frame — tính vào băng thông thật của cổng 7777. */
    private static final int LENGTH_PREFIX_BYTES = 4;

    /** opcode(2) + protocolVersion(1) + sequenceId(4) — khung ngắn hơn không có nổi header. */
    private static final int MIN_FRAME_BYTES = 7;
    // Cùng trần với GatewayServer.MAX_FRAME_LENGTH và client-core GameConnection (1MB).
    private static final int MAX_FRAME_BYTES = 1024 * 1024;

    /**
     * Trần số frame "không phải cái đang chờ" được bỏ qua trong lúc bắt tay. Đủ rộng cho lưu lượng
     * AoI/chat ở N=2000 (đo thật: tệ nhất chỉ vài frame), đủ hẹp để không treo vô hạn nếu server
     * thật sự quên trả lời.
     */
    private static final int MAX_FRAMES_SKIPPED_WHILE_WAITING = 200;

    // Cùng nhịp heartbeat của client thật (GameConnection client-core, NETWORK.md §10: ~15s).
    private static final long KEEPALIVE_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(15);

    // ADR-027 — CÙNG cert dev/test dùng ở server-gateway/client-core (xem comment tương ứng ở đó,
    // KHÔNG an toàn cho môi trường thật). Nạp từ CLASSPATH chứ không từ đường dẫn tham số: cwd của
    // Gradle là thư mục subproject chứ không phải gốc repo (bẫy lặp lại nhiều lần trong dự án),
    // classpath resource không có chỗ nào để sai.
    private static final String PINNED_CERT_RESOURCE = "tls/dev-cert.pem";
    private static final SSLSocketFactory SSL_SOCKET_FACTORY = buildPinnedSocketFactory();

    /** Địa chỉ gateway — gộp lại để chữ ký không vượt ParameterNumber (CODING-STANDARDS max 4). */
    public record GatewayEndpoint(String host, int port, int readTimeoutMillis) {}

    /**
     * Quan sát MỌI frame đi qua dây. Hai công cụ dùng nó cho hai việc khác nhau:
     * {@link SimulatedPlayer} đếm băng thông + nhặt {@code ItemGrantedEvent}; {@code probe} in ra
     * dòng đã giải mã + cập nhật ảnh chụp thế giới. Đặt ở tầng này (không phải ở từng nơi gọi) vì
     * sự kiện server tự đẩy xuống xen được giữa BẤT KỲ lượt đọc nào.
     */
    public interface FrameObserver {

        /** Null Object — dùng khi người gọi không quan tâm frame nào đi qua. */
        FrameObserver NONE = new FrameObserver() {};

        /**
         * Gọi sau mỗi frame đã gửi.
         *
         * @param frameBytes tổng byte thật đã ghi ra socket, GỒM 4-byte length prefix
         * @throws IOException nếu bản thân việc quan sát hỏng (vd. ghi ra file log)
         */
        default void onFrameSent(int frameBytes) throws IOException {
            // Mặc định không quan tâm — xem FrameObserver#NONE.
        }

        /**
         * Gọi sau mỗi frame đã nhận.
         *
         * @param frameBytes tổng byte thật đã đọc từ socket, GỒM 4-byte length prefix
         * @throws IOException nếu payload không parse được, hoặc việc quan sát hỏng
         */
        default void onFrameReceived(PacketCodec.Frame frame, int frameBytes) throws IOException {
            // Mặc định không quan tâm — xem FrameObserver#NONE.
        }
    }

    private final SSLSocket socket;
    private final DataInputStream input;
    private final DataOutputStream output;
    private final FrameObserver observer;
    private int nextSequenceId = 1;
    private long lastSentNanos = System.nanoTime();

    private GameConnection(SSLSocket socket, FrameObserver observer) throws IOException {
        this.socket = socket;
        this.observer = observer;
        this.input = new DataInputStream(socket.getInputStream());
        this.output = new DataOutputStream(socket.getOutputStream());
    }

    /**
     * Mở socket TLS và bắt tay xong TLS — CHƯA gửi {@code ConnectRequest} (xem {@link #connect}).
     *
     * @throws IOException nếu không nối được hoặc bắt tay TLS hỏng (cert sai, server tắt TLS)
     */
    public static GameConnection open(GatewayEndpoint endpoint, FrameObserver observer) throws IOException {
        SSLSocket socket = (SSLSocket) SSL_SOCKET_FACTORY.createSocket(endpoint.host(), endpoint.port());
        socket.setSoTimeout(endpoint.readTimeoutMillis());
        socket.startHandshake();
        return new GameConnection(socket, observer);
    }

    /**
     * Đổi thời gian chờ đọc. Client QUAN SÁT cần ngưỡng ngắn để vòng lặp còn nhịp gửi lệnh xen kẽ,
     * trong khi lúc bắt tay lại cần ngưỡng dài — nên hai pha dùng hai giá trị khác nhau.
     *
     * @throws SocketException nếu socket đã đóng
     */
    public void setReadTimeoutMillis(int readTimeoutMillis) throws SocketException {
        socket.setSoTimeout(readTimeoutMillis);
    }

    /**
     * Gửi 1 frame, tự cấp {@code sequenceId} tăng dần cho kết nối này.
     *
     * @return {@code sequenceId} đã gắn — server lặp lại đúng số này ở phản hồi, dùng để ghép
     *     phản hồi với lệnh ({@link RoundTrip})
     * @throws IOException nếu socket đã chết
     */
    @Override
    public int send(int opcode, byte[] payload) throws IOException {
        int sequenceId = nextSequenceId++;
        PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, sequenceId);
        byte[] frame = PacketCodec.encodeFrame(header, payload);
        output.writeInt(frame.length);
        output.write(frame);
        output.flush();
        lastSentNanos = System.nanoTime();
        observer.onFrameSent(LENGTH_PREFIX_BYTES + frame.length);
        return sequenceId;
    }

    /**
     * Server đóng kết nối im lặng quá {@code READER_IDLE_TIMEOUT} 45s (NETWORK.md §10, từ
     * 2026-10-02). Client thật có luồng heartbeat riêng; công cụ này một luồng, nên gửi Ping ngay
     * trước lượt đọc nếu đã lâu không gửi gì — probe chỉ ĐỌC suốt {@code --observeSeconds} dài (không
     * có mục tiêu) hay lúc chờ lời thách Arena sẽ không bị đá giữa chừng. {@code PongResponse} tới
     * sau được mọi chỗ đọc bỏ qua như mọi event xen giữa khác.
     */
    private void sendKeepaliveIfQuiet() throws IOException {
        if (System.nanoTime() - lastSentNanos < KEEPALIVE_INTERVAL_NANOS) {
            return;
        }
        send(
                Opcode.OPCODE_PING_VALUE,
                PingCommand.newBuilder()
                        .setClientTimestampMs(System.currentTimeMillis())
                        .build()
                        .toByteArray());
    }

    /**
     * Đọc đúng 1 frame kế tiếp, bất kể opcode.
     *
     * @throws java.net.SocketTimeoutException nếu hết thời gian chờ mà không frame nào tới
     * @throws IOException nếu socket chết hoặc frame hỏng
     */
    @Override
    public PacketCodec.Frame readFrame() throws IOException {
        sendKeepaliveIfQuiet();
        int length = checkedFrameLength(input.readInt());
        byte[] bytes = new byte[length];
        input.readFully(bytes);
        PacketCodec.Frame frame = PacketCodec.decodeFrame(bytes);
        observer.onFrameReceived(frame, LENGTH_PREFIX_BYTES + length);
        return frame;
    }

    /**
     * Độ dài khung đọc từ dây phải nằm trong [header, trần] — cùng trần 1MB với server
     * ({@code GatewayServer.MAX_FRAME_LENGTH}) và client thật. Bản cũ tin thẳng: một length âm nổ
     * {@code NegativeArraySizeException}, một length quá ngắn nổ {@code IllegalArgumentException} ở
     * {@code decodeFrame} — đều là unchecked, giết luồng client IM LẶNG (không đếm lỗi nào) trong khi
     * client đó vẫn được tính "đang chạy". Nay thành {@link IOException} ⇒ đi đúng đường đếm lỗi.
     *
     * @throws IOException nếu độ dài nằm ngoài khoảng hợp lệ
     */
    static int checkedFrameLength(int length) throws IOException {
        if (length < MIN_FRAME_BYTES || length > MAX_FRAME_BYTES) {
            throw new IOException("Độ dài khung không hợp lệ trên dây: " + length);
        }
        return length;
    }

    /**
     * Đọc tới khi gặp đúng opcode mong đợi, BỎ QUA sự kiện server tự đẩy xuống xen giữa.
     *
     * <p><b>Hai lỗi của bản cũ, cùng một chỗ (phát hiện 2026-09-05 khi đo capacity):</b>
     *
     * <p>(1) Bản cũ đọc frame theo THỨ TỰ rồi {@code parseFrom} thẳng payload, không kiểm opcode.
     * Server trả {@code ErrorResponse} thì client đem byte đó parse thành {@code ConnectResponse}
     * và nổ {@code InvalidWireTypeException}, bị đếm vào cùng {@code errorCount} với lỗi QUÁ TẢI
     * ⇒ <b>không phân biệt được "server quá tải" với "server từ chối và nói rõ lý do"</b>. Một lần
     * chạy thật cho 1000/1000 lỗi kiểu này (409 {@code error.character_limit_exceeded}) và trông y
     * hệt sập vì tải.
     *
     * <p>(2) Bắt tay KHÔNG độc quyền dòng byte. {@code handleSelectCharacterBlocking} gọi
     * {@code registry().register()} và {@code attachCharacter()} TRƯỚC khi gửi
     * {@code SelectCharacterResponse}, nên world có thể đẩy {@code CHARACTER_SPAWNED_EVENT} (303)
     * hay {@code CHAT_MESSAGE_EVENT} (802) xuống trước. Ở N=1000 đo được 431+63 client dính đúng
     * cảnh này. Client THẬT điều phối theo opcode nên không sao — chỉ công cụ đọc theo vị trí mới
     * hỏng. Vì vậy: bỏ qua, đừng coi là lỗi.
     *
     * <p>{@code ErrorResponse} thì KHÔNG bỏ qua — đó là câu trả lời cho chính yêu cầu vừa gửi.
     *
     * @param what tên message đang chờ, chỉ để ghi vào thông báo lỗi cho người đọc
     * @throws IOException nếu server trả lỗi, hoặc không thấy opcode cần sau ngân sách bỏ qua
     */
    public PacketCodec.Frame expectFrame(int expectedOpcode, String what) throws IOException {
        for (int skipped = 0; skipped < MAX_FRAMES_SKIPPED_WHILE_WAITING; skipped++) {
            PacketCodec.Frame frame = readFrame();
            int actual = frame.header().opcode();
            if (actual == expectedOpcode) {
                return frame;
            }
            if (actual == Opcode.OPCODE_ERROR_VALUE) {
                ErrorResponse error = ErrorResponse.parseFrom(frame.payload());
                throw new IOException("Server từ chối lúc chờ " + what + ": mã " + error.getErrorCode() + " ("
                        + error.getErrorKey() + ")");
            }
        }
        throw new IOException("Không thấy " + what + " (opcode " + expectedOpcode + ") sau "
                + MAX_FRAMES_SKIPPED_WHILE_WAITING + " frame — server đẩy sự kiện liên tục hoặc đã bỏ quên phản hồi");
    }

    /**
     * Giống {@link #expectFrame} nhưng KHÔNG dừng ở {@code ErrorResponse} và cho ngân sách bỏ qua
     * rộng hơn — dùng trong vòng chơi khi AoI event xen giữa liên tục.
     *
     * @throws IOException nếu không thấy opcode cần sau {@code maxFramesToSkip} frame
     */
    public PacketCodec.Frame readUntilOpcode(int expectedOpcode, int maxFramesToSkip) throws IOException {
        for (int attempt = 0; attempt < maxFramesToSkip; attempt++) {
            PacketCodec.Frame frame = readFrame();
            if (frame.header().opcode() == expectedOpcode) {
                return frame;
            }
        }
        throw new IOException("Không thấy opcode " + expectedOpcode + " sau " + maxFramesToSkip
                + " frame — server có quá tải không?");
    }

    /**
     * Đăng nhập bằng tên tài khoản.
     *
     * @throws IOException nếu server từ chối hoặc không trả lời (xem {@link #expectFrame})
     */
    public ConnectResponse connect(String username) throws IOException {
        ConnectRequest request =
                ConnectRequest.newBuilder().setUsername(username).build();
        send(Opcode.OPCODE_CONNECT_REQUEST_VALUE, request.toByteArray());
        return ConnectResponse.parseFrom(expectFrame(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, "ConnectResponse")
                .payload());
    }

    /**
     * Liệt kê nhân vật của tài khoản đang đăng nhập.
     *
     * @throws IOException nếu server từ chối hoặc không trả lời (xem {@link #expectFrame})
     */
    public ListCharactersResponse listCharacters() throws IOException {
        send(
                Opcode.OPCODE_LIST_CHARACTERS_REQUEST_VALUE,
                ListCharactersRequest.getDefaultInstance().toByteArray());
        return ListCharactersResponse.parseFrom(
                expectFrame(Opcode.OPCODE_LIST_CHARACTERS_RESPONSE_VALUE, "ListCharactersResponse")
                        .payload());
    }

    /**
     * {@code idempotencyKey} sinh mới mỗi lần gọi — đúng ngữ nghĩa "một ý định tạo mới".
     *
     * @throws IOException nếu server từ chối hoặc không trả lời (xem {@link #expectFrame})
     */
    public CreateCharacterResponse createCharacter(String name) throws IOException {
        CreateCharacterRequest request = CreateCharacterRequest.newBuilder()
                .setName(name)
                .setIdempotencyKey(UUID.randomUUID().toString())
                .build();
        send(Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE, request.toByteArray());
        return CreateCharacterResponse.parseFrom(
                expectFrame(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE, "CreateCharacterResponse")
                        .payload());
    }

    /**
     * Chọn nhân vật để vào world.
     *
     * @throws IOException nếu server từ chối hoặc không trả lời (xem {@link #expectFrame})
     */
    public SelectCharacterResponse selectCharacter(long characterId) throws IOException {
        SelectCharacterRequest request =
                SelectCharacterRequest.newBuilder().setCharacterId(characterId).build();
        send(Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE, request.toByteArray());
        return SelectCharacterResponse.parseFrom(
                expectFrame(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE, "SelectCharacterResponse")
                        .payload());
    }

    @Override
    public void close() {
        try {
            socket.close();
        } catch (IOException e) {
            LOG.debug("Lỗi đóng socket — bỏ qua, đang shutdown", e);
        }
    }

    /** Xây 1 lần lúc load class — {@link SSLSocketFactory} PIN đúng cert (ADR-027). */
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
            throw new IllegalStateException("Không load được cert pin (" + PINNED_CERT_RESOURCE + ") — ADR-027", e);
        }
    }
}
