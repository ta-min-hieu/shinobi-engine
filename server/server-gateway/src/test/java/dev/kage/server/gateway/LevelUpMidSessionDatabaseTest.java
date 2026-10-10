package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.Optional;
import java.util.UUID;

import javax.sql.DataSource;

import com.google.protobuf.InvalidProtocolBufferException;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.character.domain.ExperienceCurve;
import dev.kage.protocol.AttackCommand;
import dev.kage.protocol.ClaimQuestRewardCommand;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ConnectRequest;
import dev.kage.protocol.ConnectResponse;
import dev.kage.protocol.CreateCharacterRequest;
import dev.kage.protocol.CreateCharacterResponse;
import dev.kage.protocol.ExperienceChangedEvent;
import dev.kage.protocol.MonsterSpawnedEvent;
import dev.kage.protocol.MoveCommand;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SelectCharacterRequest;
import dev.kage.protocol.SelectCharacterResponse;
import dev.kage.protocol.SetTargetCommand;
import dev.kage.protocol.SkillResultEvent;
import dev.kage.protocol.UseSkillCommand;
import dev.kage.protocol.wire.PacketCodec;
import dev.kage.protocol.wire.PacketHeader;
import dev.kage.protocol.wire.ProtocolVersion;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.DbMetricsPort;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.database.ServerDatabaseBootstrap;
import dev.kage.server.database.session.RedisConfig;

/**
 * Lên cấp GIỮA PHIÊN phải mở khoá luật phụ thuộc cấp NGAY, không cần đăng nhập lại — trên Postgres +
 * Redis thật, bằng CHÍNH phép lắp của production ({@link GatewayBootstrap#assemble}), qua socket TCP
 * thật.
 *
 * <p><b>Bug thật trước 2026-10-10.</b> Cấp mà world đọc ({@code CharacterLevelComponent}) chỉ được gắn
 * lúc nhân vật vào world. Nhân vật Lv49 lên Lv50 giữa phiên được báo "lên cấp" và HUD hiện Lv50,
 * nhưng Tuyệt Kỹ (cổng Lv50) vẫn bị từ chối âm thầm tới lần đăng nhập sau.
 *
 * <p>Hai ô, hai đường lên cấp THẬT: EXP hạ quái (chạy trên {@code lootExecutor}) và EXP thưởng quest
 * (đồng bộ sau lĩnh thưởng). Nhân vật được đặt sẵn ở Lv49, thiếu đúng 1 EXP — bằng SQL, vì cày 122 nghìn
 * EXP không phải thứ đang được đo.
 *
 * <p><b>"Bị từ chối" được đo tất định, không bằng cách ngủ chờ.</b> World xử lý lệnh theo thứ tự tới
 * và ghi kết quả skill ra kênh ngay lúc giải. Gửi một {@code MoveCommand} đứng yên SAU lệnh skill rồi
 * đọc tới {@code MoveResponse} của nó: nếu Tuyệt Kỹ được tung thì {@code SkillResultEvent} đã phải nằm
 * trước gói đó.
 */
@Testcontainers
@Timeout(240)
class LevelUpMidSessionDatabaseTest {

    private static final int POSTGRES_PORT = 5432;
    private static final int REDIS_PORT = 6379;
    private static final String REDIS_PASSWORD = "level-sync-redis-password";
    private static final int SOCKET_TIMEOUT_MILLIS = 10_000;
    private static final int FRAMES_BEFORE_GIVING_UP = 5_000;

    private static final String ULTIMATE = "hoa_diem_nguc";
    private static final int GATE_LEVEL = 50;
    private static final String DUMMY_CODE = "training_dummy";
    private static final String DUMMY_QUEST_CODE = "kill_training_dummies";

    /** Đứng cách mộc nhân 1m — trong tầm đánh thường (2m) lẫn tầm Tuyệt Kỹ. */
    private static final float DISTANCE_TO_DUMMY = 1f;

    private static final float SAME_SPAWN_TOLERANCE = 0.5f;

    /** Dài hơn cooldown đánh thường của server (1s) — gửi nhanh hơn thì lệnh bị bỏ âm thầm. */
    private static final long ATTACK_INTERVAL_MILLIS = 1_100L;

    /** 60 nhịp × 1,1s: dư cho ~5 nhát hạ mộc nhân kể cả trượt, và cho 10s chờ nó hồi sinh. */
    private static final int ROUNDS_BEFORE_GIVING_UP = 60;

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

    @AfterEach
    void shutDownServers() {
        running.forEach(server -> server.shutdown().run());
        running.clear();
    }

    @Test
    void ultimateShouldUnlockRightAway_whenKillingAMonsterCrossesTheLevelGate() throws Exception {
        GatewayBootstrap.Assembly server = startServer();
        float dummyX = queryFloat(
                "SELECT s.pos_x FROM monster_spawn s JOIN monster_template t ON t.id = s.monster_template_id"
                        + " WHERE t.code = ? AND s.world_code = 'main' ORDER BY s.pos_x LIMIT 1",
                DUMMY_CODE);
        try (Player player = Player.connect(server)) {
            player.logIn("lvkill" + suffix());
            long characterId = player.createCharacter();
            placeOneExpBelowGate(characterId, dummyX - DISTANCE_TO_DUMMY);
            player.select(characterId);
            long dummyId = player.awaitDummyAt(dummyX);

            assertThat(player.castsUltimateAt(dummyId))
                    .as("Lv%s lúc vào world: Tuyệt Kỹ phải bị từ chối", GATE_LEVEL - 1)
                    .isFalse();

            player.send(
                    Opcode.OPCODE_SET_TARGET_COMMAND_VALUE,
                    SetTargetCommand.newBuilder()
                            .setTargetEntityId(dummyId)
                            .build()
                            .toByteArray());
            ExperienceChangedEvent leveledUp = player.attackUntilLeveledUp();
            assertThat(leveledUp.getLevel()).isEqualTo(GATE_LEVEL);
            assertThat(queryLong("SELECT level FROM character WHERE id = ?", characterId))
                    .isEqualTo(GATE_LEVEL);

            // Mộc nhân vừa bị hạ — Tuyệt Kỹ cần một mục tiêu còn sống, chờ nó hồi sinh tại chỗ.
            long respawnedDummyId = player.awaitDummyAt(dummyX);
            assertThat(player.castsUltimateAt(respawnedDummyId))
                    .as("đã Lv%s trong CÙNG phiên: Tuyệt Kỹ phải tung được, không cần đăng nhập lại", GATE_LEVEL)
                    .isTrue();
        }
    }

    @Test
    void ultimateShouldUnlockRightAway_whenAQuestRewardCrossesTheLevelGate() throws Exception {
        GatewayBootstrap.Assembly server = startServer();
        float dummyX = queryFloat(
                "SELECT s.pos_x FROM monster_spawn s JOIN monster_template t ON t.id = s.monster_template_id"
                        + " WHERE t.code = ? AND s.world_code = 'main' ORDER BY s.pos_x LIMIT 1",
                DUMMY_CODE);
        try (Player player = Player.connect(server)) {
            player.logIn("lvquest" + suffix());
            long characterId = player.createCharacter();
            placeOneExpBelowGate(characterId, dummyX - DISTANCE_TO_DUMMY);
            // Quest đã làm xong, chỉ còn lĩnh thưởng (+EXP) — phần đang được đo là đường SAU lĩnh thưởng.
            update(
                    "INSERT INTO character_quest_progress"
                            + " (character_id, quest_template_id, status, progress_json, updated_at)"
                            + " SELECT ?, id, 'COMPLETED', '{\"killed\":3}', now() FROM quest_template WHERE code = ?",
                    characterId,
                    DUMMY_QUEST_CODE);
            player.select(characterId);
            long dummyId = player.awaitDummyAt(dummyX);

            assertThat(player.castsUltimateAt(dummyId))
                    .as("Lv%s lúc vào world: Tuyệt Kỹ phải bị từ chối", GATE_LEVEL - 1)
                    .isFalse();

            player.send(
                    Opcode.OPCODE_CLAIM_QUEST_REWARD_COMMAND_VALUE,
                    ClaimQuestRewardCommand.newBuilder()
                            .setQuestCode(DUMMY_QUEST_CODE)
                            .setIdempotencyKey(UUID.randomUUID().toString())
                            .build()
                            .toByteArray());
            assertThat(ClaimQuestRewardResponse.parseFrom(player.await(Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE)
                                    .payload())
                            .getOutcome())
                    .isEqualTo(ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK);
            // Gói đứng NGAY sau câu trả lời lĩnh thưởng trên cùng luồng xử lý lệnh đó.
            ExperienceChangedEvent leveledUp = ExperienceChangedEvent.parseFrom(
                    player.await(Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE).payload());
            assertThat(leveledUp.getLeveledUp()).isTrue();
            assertThat(leveledUp.getLevel()).isEqualTo(GATE_LEVEL);

            assertThat(player.castsUltimateAt(dummyId))
                    .as("đã Lv%s trong CÙNG phiên: Tuyệt Kỹ phải tung được, không cần đăng nhập lại", GATE_LEVEL)
                    .isTrue();
        }
    }

    // ---------------------------------------------------------------------------------------------

    /** Lv49, thiếu đúng 1 EXP để lên Lv50, hệ Hỏa (chủ của {@link #ULTIMATE}), đứng tại {@code x}. */
    private static void placeOneExpBelowGate(long characterId, float x) {
        long expAtGate = 0L;
        for (int level = 1; level < GATE_LEVEL; level++) {
            expAtGate += ExperienceCurve.expForNextLevel(level);
        }
        update(
                "UPDATE character SET faction = 'HOA', level = ?, exp = ?, pos_x = ? WHERE id = ?",
                GATE_LEVEL - 1,
                expAtGate - 1L,
                x,
                characterId);
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

    private static long queryLong(String sql, Object... params) {
        return query(sql, rows -> rows.getLong(1), params);
    }

    private static float queryFloat(String sql, Object... params) {
        return query(sql, rows -> rows.getFloat(1), params);
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
            assertThat(statement.executeUpdate()).as("số dòng ghi bởi: %s", sql).isEqualTo(1);
        } catch (SQLException e) {
            throw new IllegalStateException("ghi test-only lỗi: " + sql, e);
        }
    }

    /** Một client qua socket TCP thật — đọc đồng bộ. */
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

        void select(long characterId) throws IOException {
            send(
                    Opcode.OPCODE_SELECT_CHARACTER_REQUEST_VALUE,
                    SelectCharacterRequest.newBuilder()
                            .setCharacterId(characterId)
                            .build()
                            .toByteArray());
            assertThat(SelectCharacterResponse.parseFrom(await(Opcode.OPCODE_SELECT_CHARACTER_RESPONSE_VALUE)
                                    .payload())
                            .getSuccess())
                    .isTrue();
        }

        /** Id của mộc nhân (vừa) xuất hiện tại điểm spawn {@code dummyX} — lần đầu hoặc sau khi hồi sinh. */
        long awaitDummyAt(float dummyX) throws IOException, InterruptedException {
            for (int round = 0; round < ROUNDS_BEFORE_GIVING_UP; round++) {
                for (PacketCodec.Frame frame : framesUntilNextMoveResponse()) {
                    Optional<Long> dummyId = dummyIdIfSpawnedAt(frame, dummyX);
                    if (dummyId.isPresent()) {
                        return dummyId.get();
                    }
                }
                Thread.sleep(ATTACK_INTERVAL_MILLIS);
            }
            throw new AssertionError("không thấy mộc nhân nào xuất hiện tại x=" + dummyX);
        }

        private static Optional<Long> dummyIdIfSpawnedAt(PacketCodec.Frame frame, float dummyX)
                throws InvalidProtocolBufferException {
            if (frame.header().opcode() != Opcode.OPCODE_MONSTER_SPAWNED_EVENT_VALUE) {
                return Optional.empty();
            }
            MonsterSpawnedEvent spawned = MonsterSpawnedEvent.parseFrom(frame.payload());
            boolean isTheDummy = DUMMY_CODE.equals(spawned.getMonsterCode())
                    && Math.abs(spawned.getX() - dummyX) < SAME_SPAWN_TOLERANCE;
            return isTheDummy ? Optional.of(spawned.getMonsterId()) : Optional.empty();
        }

        /** Gửi Tuyệt Kỹ; {@code true} nếu world đã tung nó (trúng hay trượt đều tính) — xem javadoc lớp. */
        boolean castsUltimateAt(long targetEntityId) throws IOException {
            send(
                    Opcode.OPCODE_USE_SKILL_COMMAND_VALUE,
                    UseSkillCommand.newBuilder()
                            .setSkillCode(ULTIMATE)
                            .setTargetEntityId(targetEntityId)
                            .build()
                            .toByteArray());
            for (PacketCodec.Frame frame : framesUntilNextMoveResponse()) {
                if (frame.header().opcode() == Opcode.OPCODE_SKILL_RESULT_EVENT_VALUE
                        && ULTIMATE.equals(
                                SkillResultEvent.parseFrom(frame.payload()).getSkillCode())) {
                    return true;
                }
            }
            return false;
        }

        /** Đánh thường mục tiêu đang nhắm cho tới khi server báo CHÍNH lượt cộng EXP đó làm lên cấp. */
        ExperienceChangedEvent attackUntilLeveledUp() throws IOException, InterruptedException {
            for (int round = 0; round < ROUNDS_BEFORE_GIVING_UP; round++) {
                send(
                        Opcode.OPCODE_ATTACK_COMMAND_VALUE,
                        AttackCommand.getDefaultInstance().toByteArray());
                for (PacketCodec.Frame frame : framesUntilNextMoveResponse()) {
                    if (frame.header().opcode() != Opcode.OPCODE_EXPERIENCE_CHANGED_EVENT_VALUE) {
                        continue;
                    }
                    ExperienceChangedEvent experience = ExperienceChangedEvent.parseFrom(frame.payload());
                    if (experience.getLeveledUp()) {
                        return experience;
                    }
                }
                Thread.sleep(ATTACK_INTERVAL_MILLIS);
            }
            throw new AssertionError("không lên cấp sau " + ROUNDS_BEFORE_GIVING_UP + " nhát đánh");
        }

        /**
         * Gửi một {@code MoveCommand} đứng yên rồi trả mọi gói tới TRƯỚC {@code MoveResponse} của nó —
         * tức mọi thứ world đã ghi ra cho tới khi xử lý xong các lệnh gửi trước lời gọi này.
         */
        private List<PacketCodec.Frame> framesUntilNextMoveResponse() throws IOException {
            send(
                    Opcode.OPCODE_MOVE_COMMAND_VALUE,
                    MoveCommand.newBuilder().setDirX(0f).build().toByteArray());
            List<PacketCodec.Frame> frames = new ArrayList<>();
            for (int i = 0; i < FRAMES_BEFORE_GIVING_UP; i++) {
                PacketCodec.Frame frame = readFrame();
                if (frame.header().opcode() == Opcode.OPCODE_MOVE_RESPONSE_VALUE) {
                    return frames;
                }
                frames.add(frame);
            }
            throw new AssertionError("không thấy MoveResponse sau " + FRAMES_BEFORE_GIVING_UP + " gói");
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
