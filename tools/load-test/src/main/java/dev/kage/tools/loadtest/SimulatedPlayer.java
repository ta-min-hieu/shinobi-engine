package dev.kage.tools.loadtest;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ItemGrantedEvent;
import dev.kage.protocol.ListCharactersResponse;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PingCommand;
import dev.kage.protocol.SellItemCommand;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.wire.PacketCodec;

/**
 * 1 người chơi giả lập — nói chuyện qua ĐÚNG giao thức dây ({@link GameConnection}: socket TLS +
 * protobuf, giống hệt {@code GatewayServerIntegrationTest}), KHÔNG gọi tắt API nội bộ server. Vòng
 * lặp hành vi (BENCHMARK.md §5 "giả lập hành vi thật — move + combat + chat"): mỗi hành động là 1
 * command đi kèm phản hồi đọc thật (đo latency) hoặc fire-and-forget (SetTargetCommand).
 *
 * <p>Bán đồ (OBSERVABILITY.md §8.1 "Việc cần làm tiếp theo" — đóng khoảng trống baseline
 * {@code GoldTotalSpike}): {@code ItemGrantedEvent} rơi ra sau lúc hạ dummy được ghi nhận thụ động
 * ở {@link #onFrameReceived} (xen giữa AoI event, giống cách {@link #findTrainingDummy} bắt
 * {@code MonsterSpawnedEvent}), rồi bán lại qua {@code SellItemCommand} — đây là đường DUY NHẤT
 * trong code hiện tại làm {@code kage.gold.total} tăng thật (combat/quest chỉ cộng item/EXP).
 *
 * <p>Chỉ dummy tập luyện cố định (5,5) tồn tại trong World chính lúc viết lát cắt này — mọi người
 * chơi giả lập hội tụ về cùng 1 điểm, MÔ PHỎNG ĐÚNG kịch bản áp lực AoI/combat cao nhất hiện có
 * (không phải hạn chế của tool, phản ánh đúng nội dung game hiện tại).
 *
 * <p>⚠️ <b>KHÔNG có combat thật kể từ 2026-08-09</b> (rà 2026-10-03): {@code SetTargetCommand} từ
 * đó chỉ NHẮM, không tự ra đòn ({@code WorldCommand.SetTargetCommand}, ADR-034) — muốn đánh phải
 * gửi {@code AttackCommand}, mà công cụ này chưa bao giờ gửi. Hệ quả: không {@code AttackResultEvent},
 * không vật phẩm rơi, nhánh bán đồ bên dưới không bao giờ chạy, {@code kage_gold} đứng yên. Mọi
 * phép đo capacity sau ngày đó là tải "đi + chat + ping", KHÔNG phải "move + combat + chat" như
 * BENCHMARK.md §5 mô tả. Chưa thêm {@code AttackCommand}: đổi tải đầu vào làm số mới không so được
 * với số cũ, và cần vị trí trong tầm đánh — là quyết định của chủ dự án.
 */
final class SimulatedPlayer implements Runnable, GameConnection.FrameObserver {

    private static final Logger LOG = LoggerFactory.getLogger(SimulatedPlayer.class);
    private static final int SOCKET_TIMEOUT_MS = 5000;
    private static final float DUMMY_DIR_X = 0.7071f;
    private static final float DUMMY_DIR_Y = 0.7071f;
    // Sau N lỗi LIÊN TIẾP (socket chết hẳn — RateLimiter.shouldDisconnect ngắt kết nối, hoặc mạng
    // đứt thật), client "bỏ cuộc" thay vì spin vô thời hạn — PHÁT HIỆN THẬT (soak test 2026-07-26):
    // không có ngưỡng này, 1 client bị server ngắt vì spam chat sẽ tiếp tục thử+lỗi suốt phần đời
    // còn lại của soak test, làm lỗi cộng dồn tăng dần đều theo thời gian dù server hoàn toàn khoẻ.
    private static final int MAX_CONSECUTIVE_FAILURES = 5;
    // World chat giới hạn 5 lần/10s (ChatRateLimits, theo tài khoản) — xác suất này
    // phải đủ thấp để KHÔNG chạm ngưỡng đó dù cộng dồn nhiều cycle liên tiếp (trước là 20, gây
    // client bị ngắt kết nối hàng loạt trong soak test đầu).
    private static final int CHAT_PROBABILITY_PERCENT = 3;
    // Không giới hạn rate riêng ở server cho bán đồ (khác world chat) — xác suất chỉ để tránh MỌI
    // client cùng bán đúng 1 nhịp, không phải né rate limiter.
    private static final int SELL_PROBABILITY_PERCENT = 50;
    // Nhịp ngủ giữa hai vòng: 300 + [0, 500) ms (sleepJitter).
    private static final int CYCLE_SLEEP_MIN_MS = 300;
    private static final int CYCLE_SLEEP_JITTER_MS = 500;
    /**
     * Nhịp DỰ KIẾN giữa hai lệnh Move mở vòng (trung bình của {@link #sleepJitter}) — dùng bù
     * coordinated omission cho mẫu Move ({@link LatencyRecorder#record(long, long)}). Chỉ Move được
     * bù vì chỉ Move đi ĐỀU mỗi vòng; Ping/Chat/Sell là xác suất, không có nhịp cố định để bù.
     */
    private static final long EXPECTED_CYCLE_INTERVAL_NANOS =
            TimeUnit.MILLISECONDS.toNanos(CYCLE_SLEEP_MIN_MS + CYCLE_SLEEP_JITTER_MS / 2);

    private static final long NO_COORDINATED_OMISSION_CORRECTION = Long.MAX_VALUE;

    /**
     * Gộp mọi bộ đếm chia sẻ giữa các client — Checkstyle ParameterNumber tối đa 4 tham số.
     * {@code bytesSent}/{@code bytesReceived} đo THẬT qua chính socket (gồm 4-byte length prefix +
     * header + payload protobuf) — xấp xỉ đúng traffic cổng 7777, tách biệt hoàn toàn traffic
     * Postgres/Redis (giao thức game và giao thức DB không đi qua cùng 1 socket). Lỗi đếm TÁCH
     * THEO LOẠI ({@link FailureCounters}) — bản cũ một {@code errorCount} chung trộn bắt tay hỏng với
     * quá tải.
     */
    record Metrics(
            LatencyRecorder latencyRecorder,
            FailureCounters failures,
            AtomicLong bytesSent,
            AtomicLong bytesReceived) {}

    /**
     * Vòng đời một client — report đếm client CÒN Ở TRONG WORLD theo trạng thái này. Bản cũ đếm
     * {@code players.size()}: một client bắt tay hỏng, bỏ cuộc sau {@link #MAX_CONSECUTIVE_FAILURES}
     * lỗi liên tiếp, hay chết vì exception unchecked vẫn được in là "đang chạy" tới hết test.
     */
    enum Phase {
        CONNECTING,
        IN_WORLD,
        ENDED
    }

    /** 1 dòng {@code ItemGrantedEvent} chưa bán — {@code itemId} là {@code inventory_item.id} thật. */
    private record OwnedItem(long itemId, int quantity) {}

    private final String host;
    private final int port;
    private final String username;
    private final Metrics metrics;
    private final AtomicBoolean running = new AtomicBoolean(true);
    // 1 thread/player (Runnable chạy trên Virtual Thread riêng, LoadTestRunner), không cần đồng bộ.
    private final Deque<OwnedItem> ownedItems = new ArrayDeque<>();

    private volatile Phase phase = Phase.CONNECTING;
    // volatile: stop() đọc từ luồng dừng test, run() ghi từ luồng của client.
    private volatile GameConnection connection;

    SimulatedPlayer(String host, int port, String username, Metrics metrics) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.metrics = metrics;
    }

    Phase phase() {
        return phase;
    }

    void stop() {
        running.set(false);
        closeQuietly();
    }

    @Override
    public void run() {
        try (GameConnection openConnection =
                GameConnection.open(new GameConnection.GatewayEndpoint(host, port, SOCKET_TIMEOUT_MS), this)) {
            this.connection = openConnection;

            connectCreateSelect(openConnection);
            phase = Phase.IN_WORLD;
            long monsterId = findTrainingDummy(openConnection);

            TimedExchange exchange =
                    new TimedExchange(openConnection, metrics.latencyRecorder(), metrics.failures(), System::nanoTime);
            int consecutiveFailures = 0;
            while (running.get() && consecutiveFailures < MAX_CONSECUTIVE_FAILURES) {
                consecutiveFailures = playOneCycle(openConnection, exchange, monsterId) ? 0 : consecutiveFailures + 1;
            }
            if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                LOG.debug(
                        "Client {} bỏ cuộc sau {} lỗi liên tiếp — kết nối coi như đã chết",
                        username,
                        consecutiveFailures);
            }
        } catch (IOException e) {
            // Mọi lỗi trong vòng chơi đã được playOneCycle bắt và đếm — tới được đây chỉ có thể là
            // lúc mở socket/bắt tay (findTrainingDummy tự nuốt lỗi của nó).
            if (running.get()) { // đóng chủ động lúc test kết thúc không tính là lỗi thật.
                metrics.failures().increment(FailureCounters.Kind.HANDSHAKE);
                LOG.debug("Client {} bắt tay hỏng", username, e);
            }
        } finally {
            phase = Phase.ENDED; // kể cả khi luồng chết vì exception unchecked.
        }
    }

    @Override
    public void onFrameSent(int frameBytes) {
        metrics.bytesSent().addAndGet(frameBytes); // băng thông thật qua cổng 7777.
    }

    /**
     * Đếm MỌI frame nhận được (kể cả AoI event bị {@link RoundTrip} bỏ qua) — băng thông
     * nhận thật. Ghi nhận {@code ItemGrantedEvent} THỤ ĐỘNG ở đây (không phải nơi gọi cụ thể nào)
     * vì nó có thể xen giữa BẤT KỲ lượt đọc nào sau lúc hạ dummy — cùng lý do
     * {@link #findTrainingDummy} phải quét thay vì đợi ở 1 điểm cố định.
     */
    @Override
    public void onFrameReceived(PacketCodec.Frame frame, int frameBytes) throws IOException {
        metrics.bytesReceived().addAndGet(frameBytes);
        if (frame.header().opcode() == Opcode.OPCODE_ITEM_GRANTED_EVENT_VALUE) {
            ItemGrantedEvent event = ItemGrantedEvent.parseFrom(frame.payload());
            ownedItems.add(new OwnedItem(event.getItemId(), event.getQuantity()));
        }
    }

    private void connectCreateSelect(GameConnection openConnection) throws IOException {
        openConnection.connect(username);
        openConnection.selectCharacter(reuseOrCreateCharacter(openConnection));
    }

    /**
     * DÙNG LẠI nhân vật sẵn có của account, chỉ tạo mới khi chưa có (2026-09-05).
     *
     * <p><b>Vì sao:</b> tên account là {@code loadtest<N>} cố định giữa các lần chạy, còn bản cũ
     * thì LẦN NÀO CŨNG {@code CreateCharacterRequest}. Mỗi lần chạy đẻ thêm 1 nhân vật cho cùng
     * account, nên load test <b>tự hỏng dần</b>: đến khi chạm trần số nhân vật, server trả
     * {@code 409 error.character_limit_exceeded} và <b>100% client hỏng bắt tay</b> — nhìn y hệt
     * server sập vì tải. Đã xảy ra thật (1000/1000 lỗi) và làm mọi con số "N lỗi" trong
     * BENCHMARK.md §5.1 trở nên mơ hồ, vì chúng trộn lẫn lỗi bắt tay với lỗi quá tải.
     *
     * <p>Hệ quả phụ đáng kể: mỗi lần chạy trước đây để lại hàng nghìn nhân vật rác trong DB. Nay
     * lần chạy thứ hai trở đi <b>không tạo thêm gì cả</b>.
     *
     * @return id nhân vật để {@code SelectCharacterRequest} dùng
     */
    private long reuseOrCreateCharacter(GameConnection openConnection) throws IOException {
        ListCharactersResponse existing = openConnection.listCharacters();
        if (existing.getCharactersCount() > 0) {
            return existing.getCharacters(0).getId();
        }
        String name = "Load" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return openConnection.createCharacter(name).getCharacter().getId();
    }

    /** Best-effort — dummy tập luyện luôn trong AoI ngay sau spawn (server-world WorldFacade), vài frame đầu là đủ. */
    private long findTrainingDummy(GameConnection openConnection) {
        try {
            for (int attempt = 0; attempt < 20; attempt++) {
                PacketCodec.Frame frame = openConnection.readFrame();
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
     * CUỐI khối {@code try} — 1 lần {@link IOException}/{@link java.net.SocketTimeoutException} (bình
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
    private boolean playOneCycle(GameConnection openConnection, TimedExchange exchange, long monsterId) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            runCycleBody(openConnection, exchange, monsterId, random);
            return true;
        } catch (IOException e) {
            // Hết giờ / thiếu phản hồi / mất kết nối — đếm TÁCH loại (FailureCounters.classifyInWorld).
            if (running.get()) {
                metrics.failures().increment(FailureCounters.classifyInWorld(e));
            }
            return false;
        } finally {
            sleepJitter(random);
        }
    }

    /** Tách khỏi {@link #playOneCycle} để giữ dưới giới hạn Checkstyle MethodLength. */
    private void runCycleBody(
            GameConnection openConnection, TimedExchange exchange, long monsterId, ThreadLocalRandom random)
            throws IOException {
        exchange.run(
                Opcode.OPCODE_MOVE_COMMAND_VALUE,
                Opcode.OPCODE_MOVE_RESPONSE_VALUE,
                MoveCommand.newBuilder()
                        .setDirX(DUMMY_DIR_X)
                        .setDirY(DUMMY_DIR_Y)
                        .build()
                        .toByteArray(),
                EXPECTED_CYCLE_INTERVAL_NANOS);

        if (monsterId != 0 && random.nextInt(100) < 70) { // GDD §6 auto-target — fire-and-forget.
            SetTargetCommand setTarget =
                    SetTargetCommand.newBuilder().setTargetEntityId(monsterId).build();
            openConnection.send(Opcode.OPCODE_SET_TARGET_COMMAND_VALUE, setTarget.toByteArray());
        }
        if (random.nextInt(100) < CHAT_PROBABILITY_PERCENT) {
            exchange.run(
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE,
                    Opcode.OPCODE_SEND_CHAT_MESSAGE_RESPONSE_VALUE,
                    SendChatMessageCommand.newBuilder()
                            .setChannel(ChatChannel.CHAT_CHANNEL_WORLD)
                            .setBody("load-test " + username)
                            .build()
                            .toByteArray(),
                    NO_COORDINATED_OMISSION_CORRECTION);
        }
        if (random.nextInt(100) < 30) {
            PingCommand ping = PingCommand.newBuilder()
                    .setClientTimestampMs(System.currentTimeMillis())
                    .build();
            exchange.run(
                    Opcode.OPCODE_PING_VALUE,
                    Opcode.OPCODE_PONG_VALUE,
                    ping.toByteArray(),
                    NO_COORDINATED_OMISSION_CORRECTION);
        }
        if (!ownedItems.isEmpty() && random.nextInt(100) < SELL_PROBABILITY_PERCENT) {
            sellOneOwnedItem(exchange);
        }

        MoveCommand stop = MoveCommand.newBuilder().setDirX(0f).setDirY(0f).build();
        openConnection.send(Opcode.OPCODE_MOVE_COMMAND_VALUE, stop.toByteArray());
    }

    /**
     * Bán 1 dòng item đã nhặt được (kunai rơi từ dummy tập luyện, LUÔN nằm trong {@code shop_item}
     * — xem {@code feature-shop/CLAUDE.md}), chấp nhận outcome bất kỳ (kể cả {@code ITEM_NOT_FOUND}
     * nếu server đã dọn item đó vì lý do khác — tool chỉ cần round-trip THẬT để tạo tải kage.gold
     * thật, không assert nghiệp vụ ở đây, business logic đã có test riêng ở feature-shop).
     */
    private void sellOneOwnedItem(TimedExchange exchange) throws IOException {
        OwnedItem item = ownedItems.poll();
        exchange.run(
                Opcode.OPCODE_SELL_ITEM_COMMAND_VALUE,
                Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE,
                SellItemCommand.newBuilder()
                        .setItemId(item.itemId())
                        .setQuantity(item.quantity())
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray(),
                NO_COORDINATED_OMISSION_CORRECTION);
    }

    private void sleepJitter(ThreadLocalRandom random) {
        try {
            // jitter — tránh mọi client đồng bộ tuyệt đối.
            Thread.sleep(CYCLE_SLEEP_MIN_MS + random.nextInt(CYCLE_SLEEP_JITTER_MS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }

    private void closeQuietly() {
        if (connection != null) {
            connection.close();
        }
    }
}
