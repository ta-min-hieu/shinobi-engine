package dev.kage.server.database.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.world.domain.SafeZone;
import dev.kage.world.domain.Waypoint;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V35 ({@code safe_zone}) + V49
 * (cột {@code heals} + 6 trạm tiếp ứng) + {@link PostgresSafeZoneAdapter} (ADR-046 D3, ADR-050).
 *
 * <p>Thước đo quan trọng nhất ở đây là {@link #shouldKeepEveryZoneClearOfEveryPatrolBand()}:
 * một vùng an toàn mà quái ĐỨNG NGAY TRONG ĐÓ thì không an toàn, và không có gì báo — chỉ playtest
 * mới thấy. Biên vùng và điểm spawn quái nằm ở HAI migration khác nhau (V35 vs V25/V32), nên phải
 * có một chỗ nào đó buộc chúng nói chuyện với nhau; đây là chỗ đó.
 */
@Testcontainers
class SafeZoneIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    /**
     * Số vùng world chính: tông môn + 6 trạm tiếp ứng (V49, ADR-050 D5) + trạm cổng nội môn @240 (V109, ADR-130) +
     * hai trạm Hoang Vực sâu @304/@368 (V113, ADR-139).
     */
    private static final int EXPECTED_ZONE_COUNT = 10;

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

    /**
     * ⚠️ Ô này TỪNG khẳng định {@code hasSize(1)} — "thế giới có đúng một vùng an toàn". V49 phá điều
     * đó có chủ đích (ADR-050 D5: 6 trạm tiếp ứng dọc trục), nên thứ nó khoá nay là HAI việc khác:
     * đủ số vùng, và biên tông môn không bị đợt này đụng vào.
     */
    @Test
    void shouldSeedTheSectGroundsAndSixWayposts_onTheMainWorld() {
        List<SafeZone> zones = new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE);

        assertThat(zones).hasSize(EXPECTED_ZONE_COUNT);
        SafeZone sect = zoneByCode(zones, "sect_grounds");
        assertThat(sect.worldCode()).isEqualTo(SafeZone.MAIN_WORLD_CODE);
        assertThat(sect.minX()).isEqualTo(-15f);
        assertThat(sect.maxX()).isEqualTo(5f);
    }

    /**
     * ADR-050 D4 — hồi ĐẦY máu lúc bước vào là đặc quyền của tông môn. Trạm tiếp ứng mà cũng hồi đầy
     * thì "bước ra đánh, bước vào đầy máu, lặp lại" thắng được mọi trận cạnh trạm; thứ người chơi
     * thiếu là chỗ đứng yên 8 giây, không phải một cái nút.
     */
    @Test
    void shouldLetOnlyTheSectGroundsHealOnEntry() {
        List<SafeZone> zones = new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE);

        assertThat(zones).filteredOn(SafeZone::heals).extracting(SafeZone::code).containsExactly("sect_grounds");
        assertThat(zones).filteredOn(zone -> !zone.heals()).hasSize(EXPECTED_ZONE_COUNT - 1);
    }

    @Test
    void shouldCoverTheLoginSpot_soAPlayerComingBackIsAlwaysHealed() {
        // Nhân vật vào world ở x = 0 (ADR-041 nạp vị trí đã lưu, nhưng nhân vật mới bắt đầu ở 0).
        SafeZone sect = zoneByCode(
                new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE), "sect_grounds");

        assertThat(sect.minX()).isLessThanOrEqualTo(0f);
        assertThat(sect.maxX()).isGreaterThanOrEqualTo(0f);
    }

    /**
     * ⚠️ BẤT BIẾN ĐÃ ĐỔI (ADR-050 D5). Bản cũ đòi "không quái nào VỚI TỚI được bên trong vùng", đo
     * bằng khoảng cách tới điểm spawn gần nhất. Điều đó KHÔNG thoả được ở đuôi trục — tầm với thật
     * (tuần tra 3 + aggro 3..6) của bảy điểm spawn từ x=87 trở ra phủ kín nhau, khe trống rộng nhất
     * chỉ 2m, hẹp hơn chính tầm đánh. Và từ D1 nó cũng không còn CẦN: vùng an toàn nay an toàn nhờ
     * LUẬT (quái không nhắm người trong vùng, đang đuổi thì bỏ cuộc), không nhờ khoảng cách.
     *
     * <p>Thứ vẫn phải gác bằng máy là chuyện khác: <b>không vùng nào được chồng lên dải tuần tra của
     * một điểm spawn</b>. Quái ĐỨNG TRONG vùng an toàn là cảnh vô lý, và D1/D2 không đụng tới quái
     * nên không có gì đẩy nó ra. Biên vùng nằm ở V35/V49 còn điểm spawn nằm ở V25/V32/V45/V47/V48 —
     * hai nơi khác nhau, nên phải có một chỗ buộc chúng nói chuyện với nhau; đây là chỗ đó.
     */
    @Test
    void shouldKeepEveryZoneClearOfEveryPatrolBand() {
        List<SafeZone> zones = new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE);

        forEachPatrolBand((monsterCode, bandMinX, bandMaxX) -> {
            for (SafeZone zone : zones) {
                boolean overlaps = zone.minX() <= bandMaxX && bandMinX <= zone.maxX();
                assertThat(overlaps)
                        .as(
                                "vùng %s [%s, %s] chồng dải tuần tra của %s [%s, %s]",
                                zone.code(), zone.minX(), zone.maxX(), monsterCode, bandMinX, bandMaxX)
                        .isFalse();
            }
        });
    }

    @Test
    void shouldReturnEmpty_forAWorldThatDeclaresNoZone() {
        assertThat(new PostgresSafeZoneAdapter(dataSource).listSafeZones("dungeon"))
                .isEmpty();
    }

    /**
     * ADR-141 (V114) — ba trạm truyền tống mới đặt giữa ba vùng an toàn: dời tới là đứng trong vùng an toàn, kể cả trạm
     * 368 nằm giữa dải PvP {@code wasteland_deep}. Trạm cũ (V87) không thuộc phép đo này — V57 đã giãn trục sau đó.
     */
    @Test
    void shouldLandEveryWastelandWaypointInsideASafeZone() {
        List<SafeZone> zones = new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE);
        List<Waypoint> waypoints = new PostgresWaypointAdapter(dataSource).listWaypoints();

        assertThat(waypoints)
                .extracting(Waypoint::code)
                .contains("inner_sect_gate", "wasteland_descent", "deep_dead_vein");
        for (String code : List.of("inner_sect_gate", "wasteland_descent", "deep_dead_vein")) {
            Waypoint waypoint = waypoints.stream()
                    .filter(w -> w.code().equals(code))
                    .findFirst()
                    .orElseThrow();
            assertThat(zones).as("trạm %s @%s", code, waypoint.x()).anySatisfy(zone -> assertThat(waypoint.x())
                    .isBetween(zone.minX(), zone.maxX()));
        }
    }

    /** ADR-144 (V116) — hai vùng thời tiết đọc đủ cột, bão cát trùng dải PvP Hoang Vực sâu. */
    @Test
    void shouldSeedTheTwoWeatherRegions() {
        List<dev.kage.world.domain.WeatherRegion> regions =
                new PostgresSafeZoneAdapter(dataSource).listWeatherRegions(SafeZone.MAIN_WORLD_CODE);

        assertThat(regions)
                .extracting(dev.kage.world.domain.WeatherRegion::code)
                .containsExactly("sect_spirit_rain", "wasteland_sandstorm");
        dev.kage.world.domain.WeatherRegion storm = regions.get(1);
        assertThat(storm.kind()).isEqualTo("SANDSTORM");
        assertThat(storm.minX()).isEqualTo(307f);
        assertThat(storm.maxX()).isEqualTo(417f);
        assertThat(storm.periodMinutes()).isEqualTo(45);
    }

    private static SafeZone zoneByCode(List<SafeZone> zones, String code) {
        return zones.stream()
                .filter(zone -> zone.code().equals(code))
                .findFirst()
                .orElseThrow(() -> new AssertionError("không có vùng an toàn nào mã " + code));
    }

    /**
     * Đọc THẲNG từ seed: mỗi điểm spawn world chính thành một dải tuần tra {@code spawn ± patrol}.
     *
     * <p>⚠️ CHỈ loài {@code aggro_radius_meters > 0}. Bản đầu quét mọi điểm spawn và đỏ ngay ở hàng
     * seed CŨ: {@code blood_sand_enforcer} đứng ở {@code x = -8}, tuần tra 0 ⇒ dải {@code [-8, -8]}
     * nằm gọn trong sân tông môn {@code [-15, 5]}. Đó là nội dung CỐ Ý (STORY §6 — hộ pháp Huyết Sa
     * cải trang làm chấp sự, đứng trên hành lang arc 1; ADR-049 cho hắn {@code aggro = 0} đúng vì
     * thế), không phải lỗi. Mối nguy thật mà bất biến này gác là con CÓ THỂ GÂY SỰ đứng trong vùng;
     * con {@code aggro = 0} không bao giờ nhắm ai, và nếu người chơi đánh nó trước thì ADR-050 D3 đã
     * tước lá chắn rồi.
     */
    private static void forEachPatrolBand(PatrolBandVisitor visitor) {
        String sql = "SELECT t.code, s.pos_x, t.patrol_radius_meters FROM monster_spawn s"
                + " JOIN monster_template t ON t.id = s.monster_template_id"
                + " WHERE s.world_code = ? AND t.aggro_radius_meters > 0";
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, SafeZone.MAIN_WORLD_CODE);
            try (ResultSet rs = statement.executeQuery()) {
                assertThat(rs.isBeforeFirst())
                        .as("không có điểm spawn nào để đối chiếu — phép đo hỏng, không phải bản đồ trống")
                        .isTrue();
                while (rs.next()) {
                    float spawnX = rs.getFloat("pos_x");
                    float patrol = rs.getFloat("patrol_radius_meters");
                    visitor.visit(rs.getString("code"), spawnX - patrol, spawnX + patrol);
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Lỗi test-only đọc dải tuần tra", e);
        }
    }

    @FunctionalInterface
    private interface PatrolBandVisitor {
        void visit(String monsterCode, float bandMinX, float bandMaxX);
    }
}
