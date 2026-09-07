package dev.kage.server.database.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;
import dev.kage.world.domain.SafeZone;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V35 ({@code safe_zone}) +
 * {@link PostgresSafeZoneAdapter} (ADR-046 D3).
 *
 * <p>Thước đo quan trọng nhất ở đây là {@link #shouldPlaceTheSectGroundsSoNoMonsterCanReachInside()}:
 * một vùng an toàn mà quái đứng ngay trong đó thì không an toàn, và không có gì báo — chỉ playtest
 * mới thấy. Biên vùng và điểm spawn quái nằm ở HAI migration khác nhau (V35 vs V25/V32), nên phải
 * có một chỗ nào đó buộc chúng nói chuyện với nhau; đây là chỗ đó.
 */
@Testcontainers
class SafeZoneIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    /** Điểm spawn quái gần vùng nhất về phía ĐÔNG — mộc nhân đầu tiên (V25). */
    private static final float NEAREST_MONSTER_EAST_X = 8f;

    /** Điểm spawn quái gần vùng nhất về phía TÂY — thạch vệ sau khi V32 dời sang tây. */
    private static final float NEAREST_MONSTER_WEST_X = -20f;

    /** Tầm đánh của mọi entity ở lát cắt này ({@code CombatConstants.ATTACK_RANGE_METERS}). */
    private static final float ATTACK_RANGE_METERS = 2f;

    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:17")).withStartupTimeout(Duration.ofMinutes(2));

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
    void shouldSeedExactlyOneSafeZone_onTheMainWorld() {
        List<SafeZone> zones = new PostgresSafeZoneAdapter(dataSource).listSafeZones(SafeZone.MAIN_WORLD_CODE);

        assertThat(zones).hasSize(1);
        SafeZone sect = zones.get(0);
        assertThat(sect.code()).isEqualTo("sect_grounds");
        assertThat(sect.worldCode()).isEqualTo(SafeZone.MAIN_WORLD_CODE);
        assertThat(sect.minX()).isEqualTo(-15f);
        assertThat(sect.maxX()).isEqualTo(5f);
    }

    @Test
    void shouldCoverTheLoginSpot_soAPlayerComingBackIsAlwaysHealed() {
        // Nhân vật vào world ở x = 0 (ADR-041 nạp vị trí đã lưu, nhưng nhân vật mới bắt đầu ở 0).
        SafeZone sect = new PostgresSafeZoneAdapter(dataSource)
                .listSafeZones(SafeZone.MAIN_WORLD_CODE)
                .get(0);

        assertThat(sect.minX()).isLessThanOrEqualTo(0f);
        assertThat(sect.maxX()).isGreaterThanOrEqualTo(0f);
    }

    @Test
    void shouldPlaceTheSectGroundsSoNoMonsterCanReachInside() {
        SafeZone sect = new PostgresSafeZoneAdapter(dataSource)
                .listSafeZones(SafeZone.MAIN_WORLD_CODE)
                .get(0);

        assertThat(NEAREST_MONSTER_EAST_X - sect.maxX()).isGreaterThan(ATTACK_RANGE_METERS);
        assertThat(sect.minX() - NEAREST_MONSTER_WEST_X).isGreaterThan(ATTACK_RANGE_METERS);
    }

    @Test
    void shouldReturnEmpty_forAWorldThatDeclaresNoZone() {
        assertThat(new PostgresSafeZoneAdapter(dataSource).listSafeZones("dungeon"))
                .isEmpty();
    }
}
