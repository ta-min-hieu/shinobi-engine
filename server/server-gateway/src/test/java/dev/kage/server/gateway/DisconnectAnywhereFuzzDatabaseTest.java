package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.EquipItemCommand;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.ListInventoryRequest;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendChatMessageCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.session.RedisConfig;

/**
 * Cổng build: client rớt mạng ở BẤT KỲ thời điểm nào thì server không được để lại trạng thái hỏng.
 *
 * <p>Mỗi vòng chạy một chuỗi lệnh ngẫu nhiên có seed ({@link DisconnectFuzzScript}) trên server thật
 * thu nhỏ ({@link DisconnectFuzzServer}), rồi cắt MỌI kết nối và đo TRẠNG THÁI CUỐI — không đo "có
 * ném lỗi không":
 *
 * <ol>
 *   <li>bộ nhớ: không nhân vật nào còn đăng ký, còn giữ phiên, còn định tuyến về world chính / hầm
 *       ngục / đấu trường; không World hầm ngục nào còn chạy; không vị trí nào còn bị theo dõi — và
 *       vẫn vậy sau một nhịp nữa (việc dọn bất đồng bộ không được gắn ai trở lại);
 *   <li>DB: tổng vàng và tổng từng loại vật phẩm đúng bằng lúc đầu (chỉ giao dịch làm đổi chủ), không
 *       dòng âm, tổng thắng = tổng thua;
 *   <li>không dòng log ERROR nào;
 *   <li>sống: ai cũng đăng nhập lại được dưới {@link DisconnectFuzzScript#LOGIN_BUDGET} và điều khiển
 *       được nhân vật (lệnh di chuyển có hồi âm) — bắt loại lỗi "dọn không bao giờ xong" mà ba phép
 *       đo trên không thấy.
 * </ol>
 *
 * <p><b>Chạy lại một ca đỏ.</b> Thông báo lỗi in seed của vòng và chuỗi bước:
 * {@code ./gradlew :server:server-gateway:test --tests '*DisconnectAnywhereFuzz*'
 * -Dkage.fuzz.disconnectSeed=<seed> -Dkage.fuzz.disconnectRounds=1}. Chạy dài:
 * {@code -Dkage.fuzz.disconnectRounds=500 -Djunit.jupiter.execution.timeout.mode=disabled}. Seed cố
 * định chuỗi bước, không cố định thứ tự xen kẽ giữa các luồng — và phiên giao dịch/nhóm còn lại trong
 * DB từ vòng trước cũng là một phần đầu vào (phiên giao dịch cố ý sống qua lần ngắt kết nối).
 */
@Testcontainers
@Timeout(240)
class DisconnectAnywhereFuzzDatabaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(DisconnectAnywhereFuzzDatabaseTest.class);

    private static final long DEFAULT_SEED = 20_261_010L;
    private static final int DEFAULT_ROUNDS = 10;
    private static final int DEFAULT_STEPS = 120;
    private static final long SEED = Long.getLong("kage.fuzz.disconnectSeed", DEFAULT_SEED);
    private static final int ROUNDS = Integer.getInteger("kage.fuzz.disconnectRounds", DEFAULT_ROUNDS);
    private static final int STEPS = Integer.getInteger("kage.fuzz.disconnectSteps", DEFAULT_STEPS);

    private static final int ACTOR_COUNT = 4;
    private static final long START_GOLD = 5_000L;
    private static final int START_ITEMS = 60;
    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "disconnect-fuzz-redis-password";
    private static final Duration SETTLE_DEADLINE = Duration.ofSeconds(15);
    private static final Duration MOVE_REPLY_DEADLINE = Duration.ofSeconds(3);
    private static final long POLL_MILLIS = 20L;
    /** Sau khi đã sạch, chờ thêm chừng này rồi đo lại — việc dọn muộn không được gắn ai trở lại. */
    private static final long LATE_CLEANUP_MILLIS = 150L;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static DisconnectFuzzServer server;
    private static int emptyWorldEntityCount;
    private static final List<DisconnectFuzzScript.Actor> ACTORS = new ArrayList<>();

    @BeforeAll
    static void startServer() throws InterruptedException, InvalidProtocolBufferException {
        server = new DisconnectFuzzServer(
                new DatabaseConfig(
                        POSTGRES.getHost(),
                        POSTGRES.getMappedPort(POSTGRES_PORT),
                        POSTGRES.getDatabaseName(),
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword()),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD));
        for (int i = 0; i < ACTOR_COUNT; i++) {
            ACTORS.add(createActor(i));
        }
        server.awaitAllConnectionsClosed();
        emptyWorldEntityCount = server.awaitStableMainWorldEntityCount();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void serverShouldKeepNoBrokenState_whenClientsDisconnectAtAnyPoint() throws InterruptedException {
        DisconnectFuzzServer.Economy startingEconomy = server.economy();
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            for (int round = 0; round < ROUNDS; round++) {
                long roundSeed = SEED + round;
                DisconnectFuzzScript script = new DisconnectFuzzScript(server, ACTORS, roundSeed);
                int errorsBefore = errorLog.errorCount();
                String context;
                try {
                    script.runSteps(STEPS);
                } catch (AssertionError e) {
                    throw new AssertionError(e.getMessage() + " — " + describe(roundSeed, round, script), e);
                } finally {
                    script.disconnectEveryone();
                }
                context = describe(roundSeed, round, script);
                server.awaitAllConnectionsClosed();

                assertNothingLeftBehind(context);
                assertThat(server.economy())
                        .as("vàng/vật phẩm/xếp hạng phải bảo toàn — %s", context)
                        .isEqualTo(startingEconomy);
                assertThat(errorLog.errorsSince(errorsBefore))
                        .as("không được có log ERROR — %s", context)
                        .isEmpty();
                assertEveryoneCanStillPlay(script, context);
                script.disconnectEveryone();
                server.awaitAllConnectionsClosed();
                assertNothingLeftBehind(context);
            }
        }
        LOG.info("Độ phủ của bài fuzz ngắt kết nối: {}", new TreeMap<>(DisconnectFuzzClient.COVERAGE));
        // Chống xanh RỖNG: không trận đấu / hầm ngục / phiên giao dịch nào mở được thì không lần ngắt
        // kết nối nào rơi vào giữa một việc đang dở, và mọi bất biến ở trên đúng một cách vô nghĩa.
        assertThat(DisconnectFuzzClient.COVERAGE)
                .as("bài fuzz phải thật sự mở được trận đấu, hầm ngục và phiên giao dịch")
                .containsKeys(
                        "OPCODE_DUEL_STARTED_EVENT",
                        "OPCODE_ENTER_DUNGEON_RESPONSE",
                        "OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE");
    }

    /**
     * Lệnh chạm DB còn đang chạy trên luồng khác lúc kết nối rớt, và chỉ xong SAU khi server đã dọn
     * sạch nhân vật. Bug thật (2026-10-10): mặc đồ ghi DB xong rồi mới đi tìm World của nhân vật để
     * cộng chỉ số — World không còn, {@code IllegalStateException} rơi vào lưới cuối của gateway: một
     * dòng ERROR kèm stack trace cho MỖI lần rớt mạng trúng lúc, dù không có gì hỏng cả.
     */
    @Test
    void equipFinishingAfterTheSessionDiedShouldStillSucceedQuietly() throws Exception {
        DisconnectFuzzScript.Actor actor = ACTORS.get(0);
        long swordId = server.seedItem(actor.characterId, "wooden_sword", 1);
        byte[] equip = EquipItemCommand.newBuilder()
                .setItemId(swordId)
                .setSlot(EquipmentSlot.EQUIPMENT_SLOT_WEAPON)
                .build()
                .toByteArray();

        runAfterTheSessionDied(actor, Opcode.OPCODE_EQUIP_ITEM_COMMAND_VALUE, equip);

        // Trạng thái cuối: món ĐÃ được mặc (lệnh chạy trọn phần DB), thấy được ở lần đăng nhập sau.
        assertThat(script().logInFully(actor)).isTrue();
        actor.client().discardReceived();
        actor.client()
                .send(
                        Opcode.OPCODE_LIST_INVENTORY_REQUEST_VALUE,
                        ListInventoryRequest.getDefaultInstance().toByteArray());
        PacketCodec.Frame inventory = actor.client()
                .await(Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE, MOVE_REPLY_DEADLINE)
                .orElseThrow();
        assertThat(ListInventoryResponse.parseFrom(inventory.payload()).getEquippedList())
                .extracting(EquippedSlotView::getItemId)
                .containsExactly(swordId);
        actor.client().closeWithoutWaiting();
        server.awaitAllConnectionsClosed();
        assertNothingLeftBehind("sau khi kiểm túi đồ");
    }

    /** Cùng lớp lỗi với mặc đồ, ở kênh chat khu vực: tin đã qua kiểm duyệt rồi mới đi tìm World. */
    @Test
    void areaChatFinishingAfterTheSessionDiedShouldBeDroppedQuietly() throws Exception {
        byte[] chat = SendChatMessageCommand.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_AREA)
                .setBody("xin chào")
                .build()
                .toByteArray();

        runAfterTheSessionDied(ACTORS.get(1), Opcode.OPCODE_SEND_CHAT_MESSAGE_COMMAND_VALUE, chat);
    }

    /**
     * Đăng nhập, gửi {@code opcode} nhưng GIỮ nó lại trước khi chạy, cắt kết nối, chờ server dọn xong,
     * rồi mới thả lệnh. Khẳng định: không dòng ERROR nào, và server vẫn không giữ gì về nhân vật.
     */
    private static void runAfterTheSessionDied(DisconnectFuzzScript.Actor actor, int opcode, byte[] payload)
            throws InterruptedException {
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach()) {
            assertThat(script().logInFully(actor)).isTrue();
            DisconnectFuzzServer.HeldCommand held = server.holdNextBlockingCommand();
            actor.client().send(opcode, payload);
            actor.client().closeWithoutWaiting();
            try {
                server.awaitAllConnectionsClosed();
                assertNothingLeftBehind("trước khi thả lệnh đang bay");
            } finally {
                held.release();
            }
            assertThat(held.awaitFinished(SETTLE_DEADLINE))
                    .as("lệnh bị giữ phải chạy xong")
                    .isTrue();

            assertThat(errorLog.errorsSince(0))
                    .as("lệnh xong sau khi phiên đã chết không phải là lỗi server")
                    .isEmpty();
            assertNothingLeftBehind("sau khi lệnh đang bay chạy xong");
        }
    }

    private static DisconnectFuzzScript script() {
        return new DisconnectFuzzScript(server, ACTORS, SEED);
    }

    private static String describe(long roundSeed, int round, DisconnectFuzzScript script) {
        return "seed=" + roundSeed + " (vòng " + round + ", -Dkage.fuzz.disconnectSeed=" + roundSeed
                + " -Dkage.fuzz.disconnectRounds=1)\nchuỗi bước: " + script.trace();
    }

    /** Mọi kết nối đã đóng ⇒ server phải quên sạch các nhân vật này, và không nhớ lại sau đó. */
    private static void assertNothingLeftBehind(String context) throws InterruptedException {
        List<Long> characterIds =
                ACTORS.stream().map(actor -> actor.characterId).toList();
        long deadline = System.nanoTime() + SETTLE_DEADLINE.toNanos();
        List<String> residue = server.residueOf(characterIds, emptyWorldEntityCount);
        while (!residue.isEmpty() && System.nanoTime() < deadline) {
            Thread.sleep(POLL_MILLIS);
            residue = server.residueOf(characterIds, emptyWorldEntityCount);
        }
        assertThat(residue)
                .as("server còn giữ trạng thái của nhân vật đã ngắt kết nối — %s", context)
                .isEmpty();
        Thread.sleep(LATE_CLEANUP_MILLIS);
        assertThat(server.residueOf(characterIds, emptyWorldEntityCount))
                .as("việc dọn muộn gắn nhân vật đã ngắt kết nối trở lại — %s", context)
                .isEmpty();
    }

    private static void assertEveryoneCanStillPlay(DisconnectFuzzScript script, String context)
            throws InterruptedException {
        for (DisconnectFuzzScript.Actor actor : ACTORS) {
            assertThat(script.logInFully(actor))
                    .as(
                            "%s phải đăng nhập lại được trong %s — %s",
                            actor.characterName, DisconnectFuzzScript.LOGIN_BUDGET, context)
                    .isTrue();
            actor.client().discardReceived();
            actor.client()
                    .send(
                            Opcode.OPCODE_MOVE_COMMAND_VALUE,
                            MoveCommand.newBuilder().setDirX(1f).build().toByteArray());
            assertThat(actor.client().await(Opcode.OPCODE_MOVE_RESPONSE_VALUE, MOVE_REPLY_DEADLINE))
                    .as("%s phải điều khiển được nhân vật sau khi đăng nhập lại — %s", actor.characterName, context)
                    .isPresent();
        }
    }

    private static DisconnectFuzzScript.Actor createActor(int index)
            throws InterruptedException, InvalidProtocolBufferException {
        String username = "dcfuzz" + index;
        String characterName = "DcFuzz" + index;
        DisconnectFuzzClient client = server.open(new DisconnectFuzzClient.Hints());
        client.send(
                Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
        assertThat(client.await(Opcode.OPCODE_CONNECT_RESPONSE_VALUE, DisconnectFuzzScript.LOGIN_BUDGET))
                .isPresent();
        client.send(
                Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE,
                CreateCharacterRequest.newBuilder()
                        .setName(characterName)
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build()
                        .toByteArray());
        PacketCodec.Frame created = client.await(
                        Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE, DisconnectFuzzScript.LOGIN_BUDGET)
                .orElseThrow(() -> new AssertionError("setup: không tạo được nhân vật " + characterName));
        long characterId = CreateCharacterResponse.parseFrom(created.payload())
                .getCharacter()
                .getId();
        client.closeWithoutWaiting();
        long itemId = server.seedEconomy(characterId, START_GOLD, START_ITEMS);
        return new DisconnectFuzzScript.Actor(username, characterName, characterId, itemId);
    }
}
