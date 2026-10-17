package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.engine.ecs.system.Aabb;
import dev.kage.engine.ecs.system.Platform;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.server.world.WorldFacade;

/**
 * Hàng rào cho một luật hình học mà KHÔNG module nào tự nó nhìn thấy được: <b>không điểm spawn nào
 * được nằm trong khoảng {@code x} của một bệ nhảy demo.</b>
 *
 * <p><b>Vì sao nó tồn tại (2026-09-09).</b> V37 bản đầu đặt NPC {@code senior_sister} ở {@code x=4},
 * tức GIỮA bệ thứ nhất {@code [3, 7]}. Bệ là khối 3D thật có depth test, nên trong client cô bị vẽ
 * đè tới cổ — chỉ còn cái đầu nổi trên mặt khối olive. Ba thứ đều nói "ổn" trong khi nó hỏng:
 *
 * <ul>
 *   <li>Test đơn/tích hợp: không test nào biết khái niệm "bệ nhảy".
 *   <li>{@code runWorldPreview}: đặt entity theo toạ độ của RIÊNG nó, không đọc {@code npc_spawn} —
 *       javadoc của chính nó có dòng "toàn bộ khung nằm TRƯỚC bệ demo đầu tiên", tức nó đã tự tránh
 *       cái bẫy này cho riêng mình từ lâu.
 *   <li>Ba ràng buộc vị trí đã có (vùng an toàn V35, tầm aggro V29, tầm đuổi V32) đều xanh.
 * </ul>
 *
 * Chỉ CLIENT THẬT mới nói ra. Đây là cùng họ với lỗi dải nền đất {@code [-55, 60]} (bắt được
 * 2026-09-06): một hằng số hình học không có gì nối với dữ liệu spawn trong DB thì sớm muộn cũng lệch.
 *
 * <p><b>Vì sao ở server-gateway.</b> Nó là module DUY NHẤT thấy được cả hai đầu — {@code server-world}
 * giữ {@code WorldFacade.demoPlatforms()}, còn {@code server-database} giữ migration, và
 * <b>server-database KHÔNG được phụ thuộc server-world</b> (ràng buộc chi phối mọi đường dữ liệu
 * world, ADR-037). Đọc SQL thẳng thay vì qua Port: ô này gác DỮ LIỆU, không gác adapter, và nó phải
 * thấy MỌI dòng — kể cả loại spawn nào đó thêm sau này mà chưa có Port riêng.
 */
@Testcontainers
@Timeout(180)
class SpawnGeometryIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    /** Chỉ world 'main' có bệ — dungeon/arena dựng với {@code spawnOverworldContent=false}. */
    private static final String MAIN_WORLD_CODE = "main";

    /**
     * Chép tay từ {@code DemoPlatforms.GROUND_MIN_X}/{@code GROUND_MAX_X}
     * ({@code client/client-core/src/main/java/dev/kage/client/core/world/DemoPlatforms.java}) —
     * cùng lý do {@code DEMO_PLATFORMS} ở dưới cũng chép tay thay vì import: {@code server-gateway}
     * không được phụ thuộc {@code client-core}, và ranh giới module không có cách nào khác để một
     * hằng số hình học nhìn thấy cả hai phía. Đổi một bên mà quên bên kia thì test này (phía server)
     * vẫn xanh — nó chỉ gác server tự lệch với chính hai số ghim ở đây, không gác hai module lệch
     * NHAU (xem {@code verifyDemoPlatformsMatch} trong {@code client-core/build.gradle.kts} cho vế
     * đó, nếu đã thêm).
     */
    private static final float GROUND_MIN_X = -61f; // V57 nới -55 → -61

    private static final float GROUND_MAX_X = 225f; // V57 nới 210 → 225

    @Container
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

    static DataSource dataSource;

    @BeforeAll
    static void migrate() {
        DatabaseConfig config = new DatabaseConfig(
                POSTGRES.getHost(),
                POSTGRES.getMappedPort(POSTGRES_PORT),
                POSTGRES.getDatabaseName(),
                POSTGRES.getUsername(),
                POSTGRES.getPassword());
        dataSource = PostgresDataSourceFactory.create(config);
        FlywayMigrator.migrate(dataSource);
    }

    @Test
    @DisplayName("Không NPC nào đứng trong một bệ nhảy — bệ là khối 3D, nó VẼ ĐÈ lên người đứng trong")
    void noNpcSpawnsInsideADemoPlatform() {
        assertNoneInsideAPlatform(
                readSpawns("SELECT t.code, s.pos_x FROM npc_spawn s"
                        + " JOIN npc_template t ON t.id = s.npc_template_id"
                        + " WHERE s.world_code = ?"),
                "NPC");
    }

    /**
     * Quái cũng vậy — và bất biến này đúng từ TRƯỚC khi ai viết nó ra: mọi điểm spawn quái hiện có
     * đều lọt vào khe giữa các bệ (mộc nhân @ 8 nằm giữa 7 và 9; @ 14 nằm giữa 13 và 15). Ghim lại
     * để lần thêm loài thứ sáu không phải trông vào may mắn.
     */
    @Test
    @DisplayName("Không điểm spawn quái nào nằm trong một bệ nhảy")
    void noMonsterSpawnsInsideADemoPlatform() {
        assertNoneInsideAPlatform(
                readSpawns("SELECT t.code, s.pos_x FROM monster_spawn s"
                        + " JOIN monster_template t ON t.id = s.monster_template_id"
                        + " WHERE s.world_code = ?"),
                "quái");
    }

    /**
     * Dải nền đất minh hoạ ở client-core phải phủ hết mọi điểm spawn — một bất biến bị vi phạm
     * trong im lặng BỐN lần trước khi ai viết ra một test (xem javadoc {@code GROUND_MAX_X} ở
     * {@code DemoPlatforms}). Không có gì trong DB hay compiler nối {@code [-61, 225]} với
     * {@code monster_spawn}/{@code npc_spawn}; hàng rào duy nhất là ô này.
     */
    @Test
    @DisplayName("Mọi điểm spawn (NPC lẫn quái) nằm trong dải nền đất minh hoạ [-61, 225]")
    void everySpawnFallsWithinTheGroundStrip() {
        List<Spawn> spawns = new ArrayList<Spawn>();
        spawns.addAll(readSpawns("SELECT t.code, s.pos_x FROM npc_spawn s"
                + " JOIN npc_template t ON t.id = s.npc_template_id"
                + " WHERE s.world_code = ?"));
        spawns.addAll(readSpawns("SELECT t.code, s.pos_x FROM monster_spawn s"
                + " JOIN monster_template t ON t.id = s.monster_template_id"
                + " WHERE s.world_code = ?"));
        assertThat(spawns)
                .as("không đọc được dòng spawn nào — truy vấn hỏng thì ô này vô dụng")
                .isNotEmpty();
        for (Spawn spawn : spawns) {
            assertThat(spawn.posX())
                    .as(
                            "'%s' @ x=%s nằm NGOÀI dải nền đất minh hoạ [%s, %s] — người chơi sẽ thấy"
                                    + " mình đứng lơ lửng giữa khoảng đen (playtest 2026-09-06 bắt lỗi"
                                    + " này lần đầu)",
                            spawn.code(), spawn.posX(), GROUND_MIN_X, GROUND_MAX_X)
                    .isBetween(GROUND_MIN_X, GROUND_MAX_X);
        }
    }

    /**
     * Ghim ba bệ nhảy demo phía SERVER lại literal cụ thể. Đây KHÔNG phải test hai module khớp
     * nhau (không cách nào làm việc đó bằng compiler qua ranh giới module) — nó chỉ bắt
     * {@code WorldFacade.DEMO_PLATFORMS} tự lệch khỏi chính nó theo thời gian. Đổi literal ở đây
     * thì PHẢI đổi tay {@code PLATFORMS} trong {@code client-core.DemoPlatforms} — không có gì
     * khác nhắc việc đó ngoài dòng comment ở cả hai phía.
     */
    @Test
    @DisplayName("WorldFacade.demoPlatforms() vẫn giữ đúng 3 bệ đã ghim, không âm thầm đổi")
    void demoPlatformsMatchesThePinnedLiterals() {
        // Platform/Aabb là record — so bằng equals() tự sinh, không cần helper riêng (PMD
        // ExcessiveParameterList sẽ bắt một helper 6 tham số).
        assertThat(WorldFacade.demoPlatforms())
                .containsExactly(
                        new Platform(new Aabb(3f, -0.75f, 7f, 0.75f), 0.8f),
                        new Platform(new Aabb(9f, -0.75f, 13f, 0.75f), 1.5f),
                        new Platform(new Aabb(15f, -0.75f, 19f, 0.75f), 2.2f));
    }

    private static void assertNoneInsideAPlatform(List<Spawn> spawns, String what) {
        assertThat(spawns)
                .as("không đọc được dòng spawn nào — truy vấn hỏng thì ô này vô dụng")
                .isNotEmpty();
        for (Spawn spawn : spawns) {
            for (Platform platform : WorldFacade.demoPlatforms()) {
                float minX = platform.footprint().minX();
                float maxX = platform.footprint().maxX();
                // Biên TÍNH LÀ TRONG: đứng đúng mép bệ thì nửa thân vẫn chui vào khối.
                boolean inside = spawn.posX() >= minX && spawn.posX() <= maxX;
                assertThat(inside)
                        .as(
                                "%s '%s' @ x=%s đứng TRONG bệ nhảy [%s, %s] — trong client thật nó bị khối bệ"
                                        + " vẽ đè lên (V37 §2(d)); dời sang khe giữa hai bệ",
                                what, spawn.code(), spawn.posX(), minX, maxX)
                        .isFalse();
            }
        }
    }

    private static List<Spawn> readSpawns(String sql) {
        List<Spawn> spawns = new ArrayList<Spawn>();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, MAIN_WORLD_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    spawns.add(new Spawn(rs.getString(1), rs.getFloat(2)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("đọc điểm spawn thất bại", e);
        }
        return spawns;
    }

    private record Spawn(String code, float posX) {}
}
