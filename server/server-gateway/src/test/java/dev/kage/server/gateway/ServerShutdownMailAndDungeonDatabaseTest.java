package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.sql.DataSource;

import org.assertj.core.data.Offset;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
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

import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.AttackResultEvent;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.CreatePartyCommand;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.CreatePartyResponse;
import dev.kage.protocol.EnterDungeonCommand;
import dev.kage.protocol.EnterDungeonOutcome;
import dev.kage.protocol.EnterDungeonResponse;
import dev.kage.protocol.MailAttachmentView;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.MoveResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SendMailCommand;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Hai ca tắt server giữa lúc đang chơi mà {@link ServerShutdownMidPlayDatabaseTest} chưa phủ
 * (docs/README.md dòng 76(f), thêm 2026-10-11): một loạt thư còn đang bay, và người chơi đang đứng
 * TRONG hầm ngục. Cùng phép lắp production ({@link GatewayBootstrap#assemble}), Postgres + Redis thật,
 * socket TCP thật; mọi khẳng định đo GIÁ TRỊ CUỐI trong DB sau khi trình tự tắt trả về.
 *
 * <p>Lớp riêng chứ không nối vào lớp kia: lớp kia đã hơn 800 dòng, và client socket của các test này
 * vốn mỗi lớp một bản (xem {@code LevelUpMidSessionDatabaseTest}).
 */
@Testcontainers
@Timeout(240)
class ServerShutdownMailAndDungeonDatabaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ServerShutdownMailAndDungeonDatabaseTest.class);

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "shutdown-mail-redis-password";
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;
    private static final int FRAMES_BEFORE_GIVING_UP = 5_000;
    private static final float POSITION_TOLERANCE = 0.001f;

    private static final long START_GOLD = 100_000L;
    private static final int MAILS_IN_FLIGHT = 30;
    private static final long GOLD_PER_MAIL = 10L;

    private static final String DUNGEON_CODE = "sample_dungeon";
    private static final String BOSS_CODE = "sample_boss";

    /** 5 m/s × 0,3 s ≈ 1,5 m: từ điểm vào (x = 0) tới cách boss (x = 3) chừng 1,5 m, trong tầm đánh 2 m. */
    private static final long APPROACH_MILLIS = 300L;

    /** Dày hơn cooldown đánh thường (1 đòn/giây) — lệnh thừa là no-op ở server. */
    private static final long ATTACK_INTERVAL_MILLIS = 250L;

    /** Chỗ đứng ở world chính trước khi vào hầm ngục — khác hẳn điểm vào hầm ngục (x = 0). */
    private static final float MAIN_WORLD_X = 7f;

    /** 5 m/s × 2,5 s ≈ 12,5 m trong hầm ngục: đủ xa để không thể nhầm với {@link #MAIN_WORLD_X}. */
    private static final long WALK_MILLIS = 2_500L;

    /** Lệch tối đa giữa chỗ đứng trước khi vào hầm ngục và chỗ DB còn giữ sau khi tắt. */
    private static final float RETURN_POSITION_TOLERANCE = 1f;

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    @Container
    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:8"))
            .withExposedPorts(REDIS_PORT)
            .withCommand("redis-server", "--requirepass", REDIS_PASSWORD);

    private static DataSource probe;

    private final List<GatewayBootstrap.Assembly> running = new ArrayList<>();

    @BeforeAll
    static void openProbe() {
        probe = PostgresDataSourceFactory.create(databaseConfig());
    }

    @AfterAll
    static void closeProbe() {
        PostgresDataSourceFactory.close(probe);
    }

    /** Test đỏ giữa chừng có thể bỏ lại một server chưa tắt — tắt nốt, không thì JVM test không thoát. */
    @AfterEach
    void shutDownLeftovers() {
        running.forEach(server -> server.shutdown().run());
        running.clear();
    }

    /**
     * Một loạt lệnh gửi thư kèm vàng + vật phẩm còn đang bay lúc server tắt: mỗi lệnh hoặc chạy trọn
     * (trừ vàng VÀ trừ đồ VÀ có dòng thư kèm đính kèm) hoặc không chạy gì. Vàng và vật phẩm chỉ đổi
     * chỗ từ người gửi sang thư — không biến mất, không sinh thêm.
     */
    @Test
    void mailsInFlightAtShutdownShouldBeAllOrNothing() throws Exception {
        GatewayBootstrap.Assembly server = startServer();
        long templateId = queryLong("SELECT id FROM item_template WHERE code = 'iron_scrap'");
        long senderId;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach();
                Player recipient = Player.connect(server);
                Player sender = Player.connect(server)) {
            recipient.logIn("mr" + suffix());
            long recipientId = recipient.createCharacter();
            String recipientName = queryString("SELECT name FROM character WHERE id = ?", recipientId);
            sender.logIn("ms" + suffix());
            senderId = sender.createCharacter();
            update("UPDATE character_stat SET gold = ? WHERE character_id = ?", START_GOLD, senderId);
            update(
                    "INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)"
                            + " VALUES (?, ?, ?, 0, 0)",
                    senderId,
                    templateId,
                    MAILS_IN_FLIGHT);
            sender.select(senderId);

            for (int i = 0; i < MAILS_IN_FLIGHT; i++) {
                sender.send(
                        Opcode.OPCODE_SEND_MAIL_COMMAND_VALUE,
                        SendMailCommand.newBuilder()
                                .setRecipientName(recipientName)
                                .setSubject("s" + i)
                                .setBody("b")
                                .setGoldAmount(GOLD_PER_MAIL)
                                .addAttachments(MailAttachmentView.newBuilder()
                                        .setItemTemplateId(templateId)
                                        .setQuantity(1))
                                .build()
                                .toByteArray());
            }
            shutDown(server); // không chờ câu trả lời nào

            assertThat(errorLog.errorsSince(0))
                    .as("tắt server giữa loạt lệnh gửi thư không được sinh log ERROR")
                    .isEmpty();
        }

        long mails = queryLong("SELECT count(*) FROM mail WHERE sender_id = ?", senderId);
        long goldInMails = queryLong("SELECT coalesce(sum(gold_amount), 0) FROM mail WHERE sender_id = ?", senderId);
        long itemsInMails = queryLong(
                "SELECT coalesce(sum(mi.quantity), 0) FROM mail_item mi JOIN mail m ON m.id = mi.mail_id"
                        + " WHERE m.sender_id = ?",
                senderId);
        long senderGold = queryLong("SELECT gold FROM character_stat WHERE character_id = ?", senderId);
        LOG.info("Tắt giữa {} lệnh gửi thư: {} thư đã gửi trọn", MAILS_IN_FLIGHT, mails);
        assertThat(goldInMails).as("mỗi thư đã ghi mang đúng số vàng đã đính").isEqualTo(mails * GOLD_PER_MAIL);
        assertThat(itemsInMails).as("mỗi thư đã ghi mang đúng một món").isEqualTo(mails);
        assertThat(senderGold + goldInMails)
                .as("vàng bảo toàn: người gửi + thư (đã gửi %s thư)", mails)
                .isEqualTo(START_GOLD);
        assertThat(itemQuantity(senderId, templateId) + itemsInMails)
                .as("vật phẩm bảo toàn: người gửi + thư")
                .isEqualTo(MAILS_IN_FLIGHT);
    }

    /**
     * Server tắt khi người chơi đang đứng (và đang chạy) TRONG hầm ngục. Toạ độ trong hầm ngục thuộc
     * một World khác hẳn: thứ DB phải giữ là chỗ họ đứng ở world chính trước khi vào, và lần đăng nhập
     * sau họ phải về đúng chỗ đó, điều khiển được, không còn định tuyến hầm ngục mồ côi nào.
     */
    @Test
    void playerInsideADungeonAtShutdownShouldComeBackToTheirMainWorldSpot_afterRestart() throws Exception {
        GatewayBootstrap.Assembly first = startServer();
        String username = "dg" + suffix();
        long characterId;
        List<Integer> lastOpcodes;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach();
                Player player = Player.connect(first)) {
            player.logIn(username);
            characterId = player.createCharacter();
            update("UPDATE character SET pos_x = ?, pos_y = 0 WHERE id = ?", MAIN_WORLD_X, characterId);
            assertThat(player.select(characterId).getSpawnX())
                    .isCloseTo(MAIN_WORLD_X, Offset.offset(POSITION_TOLERANCE));

            player.send(
                    Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                    CreatePartyCommand.getDefaultInstance().toByteArray());
            assertThat(CreatePartyResponse.parseFrom(player.await(Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK);
            player.send(
                    Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                    EnterDungeonCommand.newBuilder()
                            .setDungeonCode(DUNGEON_CODE)
                            .build()
                            .toByteArray());
            assertThat(EnterDungeonResponse.parseFrom(player.await(Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);

            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(-1f).build().toByteArray());
            player.await(Opcode.OPCODE_MOVE_RESPONSE_VALUE);
            Thread.sleep(WALK_MILLIS); // phím vẫn giữ, chạy ra xa MAIN_WORLD_X

            shutDown(first);

            lastOpcodes = player.readUntilClosed();
            assertThat(errorLog.errorsSince(0))
                    .as("tắt server khi có người trong hầm ngục không được sinh log ERROR")
                    .isEmpty();
        }

        assertThat(lastOpcodes)
                .as("người trong hầm ngục cũng phải được báo server tắt có chủ đích")
                .contains(Opcode.OPCODE_SERVER_SHUTTING_DOWN_EVENT_VALUE);
        float savedX = queryFloat("SELECT pos_x FROM character WHERE id = ?", characterId);
        assertThat(savedX)
                .as("DB phải giữ chỗ đứng ở WORLD CHÍNH, không phải toạ độ trong hầm ngục")
                .isCloseTo(MAIN_WORLD_X, Offset.offset(RETURN_POSITION_TOLERANCE));

        GatewayBootstrap.Assembly second = startServer();
        try (Player player = Player.connect(second)) {
            player.logIn(username);
            SelectCharacterResponse selected = player.select(characterId);
            assertThat(selected.getSpawnX()).isCloseTo(savedX, Offset.offset(POSITION_TOLERANCE));
            // Còn điều khiển được ở world chính: không định tuyến hầm ngục nào sống qua lần khởi động lại.
            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(0f).build().toByteArray());
            MoveResponse moved = MoveResponse.parseFrom(
                    player.await(Opcode.OPCODE_MOVE_RESPONSE_VALUE).payload());
            assertThat(moved.getX()).isCloseTo(savedX, Offset.offset(POSITION_TOLERANCE));
        }
        shutDown(second);
    }

    /**
     * Hạ boss trong hầm ngục rồi tắt server ngay (docs/README.md dòng 78(e)): phần thưởng của boss đi
     * cùng đường với quái world chính ({@code DungeonOutboundSink} → {@code onMonsterKilled}), nhưng
     * World phát lời báo là một World hầm ngục — dừng ở một bước khác của trình tự tắt. EXP và đồ rơi
     * phải nằm trong DB sau khi tắt.
     */
    @Test
    void bossKilledInsideADungeonRightBeforeShutdownShouldStillPayOut() throws Exception {
        prepareOneHitBossWithGuaranteedDrop();
        GatewayBootstrap.Assembly server = startServer();
        long expReward = queryLong("SELECT exp_reward FROM monster_template WHERE code = ?", BOSS_CODE);
        assertThat(expReward).as("boss phải có EXP thì phép đo mới có nghĩa").isPositive();
        long characterId;
        long itemsBefore;
        try (ErrorLogCapture errorLog = ErrorLogCapture.attach();
                Player player = Player.connect(server)) {
            player.logIn("bk" + suffix());
            characterId = player.createCharacter();
            itemsBefore = totalItems(characterId);
            player.select(characterId);
            player.send(
                    Opcode.OPCODE_CREATE_PARTY_COMMAND_VALUE,
                    CreatePartyCommand.getDefaultInstance().toByteArray());
            player.await(Opcode.OPCODE_CREATE_PARTY_RESPONSE_VALUE);
            long bossId = player.enterDungeonAndFindBoss();

            // Boss đứng cách điểm vào 3 m, tầm đánh 2 m — bước lại gần rồi đứng yên.
            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(1f).build().toByteArray());
            Thread.sleep(APPROACH_MILLIS);
            player.send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(0f).build().toByteArray());
            player.kill(bossId, characterId);

            shutDown(server); // ba việc trao thưởng vừa được xếp

            assertThat(errorLog.errorsSince(0))
                    .as("không lượt trao thưởng nào của boss bị cắt lúc tắt")
                    .isEmpty();
        }

        assertThat(queryLong("SELECT exp FROM character WHERE id = ?", characterId))
                .as("EXP của boss hạ ngay trước khi tắt")
                .isEqualTo(expReward);
        assertThat(totalItems(characterId) - itemsBefore)
                .as("đồ rơi của boss (bảng rơi đã ghim 100%)")
                .isPositive();
    }

    /**
     * Boss chết sau MỘT đòn và luôn rơi đồ — chỉ là dữ liệu của container test, cùng cách
     * {@code ServerShutdownMidPlayDatabaseTest} làm với mộc nhân. Phải chạy TRƯỚC khi dựng server.
     */
    private static void prepareOneHitBossWithGuaranteedDrop() {
        FlywayMigrator.migrate(probe);
        update("UPDATE monster_template SET max_hp = 1, dodge_percent = 0 WHERE code = ?", BOSS_CODE);
        update(
                "UPDATE drop_table_entry SET drop_chance_percent = 100"
                        + " WHERE drop_table_id = (SELECT id FROM drop_table WHERE code = ?)",
                BOSS_CODE);
    }

    private static long totalItems(long characterId) {
        return queryLong("SELECT coalesce(sum(quantity), 0) FROM inventory_item WHERE character_id = ?", characterId);
    }

    private GatewayBootstrap.Assembly startServer() throws Exception {
        ServerDatabaseBootstrap databaseBootstrap = new ServerDatabaseBootstrap(
                databaseConfig(),
                new RedisConfig(REDIS.getHost(), REDIS.getMappedPort(REDIS_PORT), REDIS_PASSWORD),
                DbMetricsPort.NO_OP);
        // Cổng 0 cho cả hai: hệ điều hành tự cấp, không đụng server dev đang chạy trên 7777/9404.
        GatewayBootstrap.Assembly server = GatewayBootstrap.assemble(
                databaseBootstrap,
                GatewayBootstrap.buildMetrics(),
                GatewayTracing.disabled(),
                new GatewayBootstrap.Endpoints(0, 0, TlsConfig.disabled()));
        running.add(server);
        GatewayBootstrap.startOrShutDown(server.server(), server.shutdown());
        return server;
    }

    private void shutDown(GatewayBootstrap.Assembly server) {
        running.remove(server);
        server.shutdown().run();
    }

    private static DatabaseConfig databaseConfig() {
        return new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
    }

    private static String suffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }

    private static long itemQuantity(long characterId, long templateId) {
        return queryLong(
                "SELECT coalesce(sum(quantity), 0) FROM inventory_item WHERE character_id = ? AND item_template_id = ?",
                characterId,
                templateId);
    }

    private static long queryLong(String sql, Object... params) {
        return query(sql, rows -> rows.getLong(1), params);
    }

    private static float queryFloat(String sql, Object... params) {
        return query(sql, rows -> rows.getFloat(1), params);
    }

    private static String queryString(String sql, Object... params) {
        return query(sql, rows -> rows.getString(1), params);
    }

    @FunctionalInterface
    private interface RowReader<T> {
        T read(ResultSet rows) throws SQLException;
    }

    private static <T> T query(String sql, RowReader<T> reader, Object... params) {
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new IllegalStateException("truy vấn test-only không trả dòng nào: " + sql);
                }
                return reader.read(rows);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("truy vấn test-only lỗi: " + sql, e);
        }
    }

    private static void update(String sql, Object... params) {
        try (Connection connection = probe.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                statement.setObject(i + 1, params[i]);
            }
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("ghi test-only lỗi: " + sql, e);
        }
    }

    /** Một client qua socket TCP thật — đọc đồng bộ, bỏ qua gói không hỏi tới. */
    private static final class Player implements AutoCloseable {

        private final Socket socket;
        private final DataInputStream in;
        private final DataOutputStream out;
        private int nextSequenceId = 1;

        private Player(Socket socket) throws IOException {
            this.socket = socket;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = new DataOutputStream(socket.getOutputStream());
        }

        static Player connect(GatewayBootstrap.Assembly server) throws IOException {
            Socket socket =
                    new Socket(InetAddress.getLoopbackAddress(), server.server().port());
            socket.setSoTimeout(SOCKET_TIMEOUT_MILLIS);
            return new Player(socket);
        }

        void logIn(String username) throws IOException {
            send(
                    Opcode.OPCODE_CONNECT_REQUEST_VALUE,
                    ConnectRequest.newBuilder().setUsername(username).build().toByteArray());
            assertThat(ConnectResponse.parseFrom(
                                    await(Opcode.OPCODE_CONNECT_RESPONSE_VALUE).payload())
                            .getSuccess())
                    .isTrue();
        }

        long createCharacter() throws IOException {
            send(
                    Opcode.OPCODE_CREATE_CHARACTER_REQUEST_VALUE,
                    CreateCharacterRequest.newBuilder()
                            .setName("Hero" + suffix())
                            .setIdempotencyKey(UUID.randomUUID().toString())
                            .build()
                            .toByteArray());
            CreateCharacterResponse created = CreateCharacterResponse.parseFrom(
                    await(Opcode.OPCODE_CREATE_CHARACTER_RESPONSE_VALUE).payload());
            assertThat(created.getSuccess()).isTrue();
            return created.getCharacter().getId();
        }

        SelectCharacterResponse select(long characterId) throws IOException {
            send(
                    Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                    SelectCharacterRequest.newBuilder()
                            .setCharacterId(characterId)
                            .build()
                            .toByteArray());
            SelectCharacterResponse selected = SelectCharacterResponse.parseFrom(
                    await(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE).payload());
            assertThat(selected.getSuccess()).isTrue();
            return selected;
        }

        void send(int opcode, byte[] payload) throws IOException {
            PacketHeader header = new PacketHeader(opcode, ProtocolVersion.CURRENT, nextSequenceId++);
            byte[] frame = PacketCodec.encodeFrame(header, payload);
            out.writeInt(frame.length);
            out.write(frame);
            out.flush();
        }

        PacketCodec.Frame await(int opcode) throws IOException {
            for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                PacketCodec.Frame frame = readFrame();
                if (frame.header().opcode() == opcode) {
                    return frame;
                }
            }
            throw new AssertionError("không thấy opcode " + opcode + " sau " + FRAMES_BEFORE_GIVING_UP + " gói");
        }

        /**
         * Vào hầm ngục rồi trả về id của boss. Đọc CẢ HAI gói trong một vòng: gói spawn của boss đi theo
         * nhịp AoI nên có thể tới trước hoặc sau câu trả lời, mà {@link #await} bỏ qua mọi gói nó không
         * hỏi tới.
         */
        long enterDungeonAndFindBoss() throws IOException {
            send(
                    Opcode.OPCODE_ENTER_DUNGEON_COMMAND_VALUE,
                    EnterDungeonCommand.newBuilder()
                            .setDungeonCode(DUNGEON_CODE)
                            .build()
                            .toByteArray());
            boolean entered = false;
            long bossId = 0L;
            for (int i = 0; i < FRAMES_BEFORE_GIVING_UP && !(entered && bossId != 0L); i++) {
                PacketCodec.Frame frame = readFrame();
                if (frame.header().opcode() == Opcode.OPCODE_ENTER_DUNGEON_RESPONSE_VALUE) {
                    assertThat(EnterDungeonResponse.parseFrom(frame.payload()).getOutcome())
                            .isEqualTo(EnterDungeonOutcome.ENTER_DUNGEON_OUTCOME_OK);
                    entered = true;
                } else if (frame.header().opcode() == Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
                    MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
                    if (spawned.getMonsterCode().equals(BOSS_CODE)) {
                        bossId = spawned.getMonsterId();
                    }
                }
            }
            assertThat(entered).as("vào được hầm ngục").isTrue();
            assertThat(bossId).as("thấy boss trong hầm ngục").isNotZero();
            return bossId;
        }

        /**
         * Nhắm rồi bấm đánh thường (luồng nền, vì luồng này bận đọc) tới khi chính nhân vật này hạ được
         * con quái đó. Trả về ngay khi gói báo "mục tiêu chết" tới — lúc đó server đã xếp ba việc trao
         * thưởng.
         */
        void kill(long monsterId, long characterId) throws IOException, InterruptedException {
            send(
                    Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                    SetTargetCommand.newBuilder()
                            .setTargetEntityId(monsterId)
                            .build()
                            .toByteArray());
            Thread attacker =
                    Thread.ofPlatform().daemon().name("attack-spammer").start(this::attackUntilInterrupted);
            try {
                for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                    PacketCodec.Frame frame = readFrame();
                    if (frame.header().opcode() != Opcode.OPCODE_ATTACK_RESULT_EVENT_VALUE) {
                        continue;
                    }
                    AttackResultEvent result = AttackResultEvent.parseFrom(frame.payload());
                    if (result.getAttackerEntityId() == characterId && result.getTargetDied()) {
                        return;
                    }
                }
            } finally {
                attacker.interrupt();
                attacker.join();
            }
            throw new AssertionError("không hạ được quái nào sau " + FRAMES_BEFORE_GIVING_UP + " gói");
        }

        private void attackUntilInterrupted() {
            byte[] attack = AttackCommand.getDefaultInstance().toByteArray();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    send(Opcode.OPCODE_ATTACK_COMMAND_VALUE, attack);
                    Thread.sleep(ATTACK_INTERVAL_MILLIS);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException e) {
                LOG.debug("Socket đóng trong lúc bấm đánh — dừng bấm", e);
            }
        }

        /** Mọi opcode còn tới cho đến khi server đóng kết nối. */
        List<Integer> readUntilClosed() throws IOException {
            List<Integer> opcodes = new ArrayList<>();
            try {
                for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                    opcodes.add(readFrame().header().opcode());
                }
            } catch (EOFException closed) {
                return opcodes;
            }
            throw new AssertionError("server không đóng kết nối sau " + FRAMES_BEFORE_GIVING_UP + " gói");
        }

        private PacketCodec.Frame readFrame() throws IOException {
            int length = in.readInt();
            byte[] bytes = new byte[length];
            in.readFully(bytes);
            return PacketCodec.decodeFrame(bytes);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
