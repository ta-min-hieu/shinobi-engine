package dev.kage.server.database.monster;

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

import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * PostgreSQL THẬT qua Testcontainers — chứng minh migration V23 (ADR-037) ÁP ĐƯỢC, không chỉ phân
 * tích cú pháp được, và adapter đọc đúng cái nó seed.
 */
@Testcontainers
class PostgresMonsterCatalogAdapterIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

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
    void shouldKeepTrainingDummyNumbersExactlyAsBeforeAdr037() {
        // Hàng ĐỐI CHỨNG của cả migration: nếu con số này trôi thì mọi test combat đang dựa vào nó
        // đổi nghĩa trong im lặng.
        MonsterTemplate dummy = findByCode("training_dummy");

        assertThat(dummy.stats().maxHp()).isEqualTo(50);
        assertThat(dummy.stats().atk()).isEqualTo(8);
        assertThat(dummy.stats().def()).isEqualTo(5);
        assertThat(dummy.stats().res()).isEqualTo(3);
        assertThat(dummy.stats().hitPercent()).isEqualTo(90);
        assertThat(dummy.expReward()).isEqualTo(20L);
        assertThat(dummy.dropTableCode()).isEqualTo("training_dummy");
    }

    @Test
    void shouldSeedStrongerSpeciesSoDefenceBecomesMeasurable() {
        // Lý do ADR-037 tồn tại: quái duy nhất có atk 8 nên DEF của V22 không đo được.
        MonsterTemplate ogre = findByCode("stone_ogre");

        assertThat(ogre.stats().atk()).isEqualTo(40);
        assertThat(ogre.stats().maxHp()).isEqualTo(200);
        assertThat(ogre.expReward()).isEqualTo(90L);
    }

    @Test
    void shouldPointEverySpeciesAtAModelKey() {
        // Không loài nào được thiếu model_key: rỗng nghĩa là client rơi về suy từ code và ra hộp.
        assertThat(new PostgresMonsterCatalogAdapter(dataSource).listTemplates())
                .isNotEmpty()
                .allSatisfy(template -> assertThat(template.modelKey()).isNotBlank());
    }

    @Test
    void shouldGiveEachSpeciesItsOwnModel() {
        // V23 cho cả ba loài dùng CHUNG `mob_training-dummy` (nợ art, ghi rõ trong migration đó);
        // V24 trả hai loài mới về model của chính chúng khi art đã vẽ xong (2026-08-30).
        //
        // Ô test này gác đúng khoảng trống đã suýt lọt: art nằm trên đĩa, `MonsterModelKey` phía
        // client đúng, mọi test khác xanh — mà trong game vẫn là ba cái cọc gỗ giống hệt nhau, vì
        // server luôn GỬI cột này và client chỉ tự suy khi nó rỗng.
        assertThat(findByCode("wandering_bandit").modelKey()).isEqualTo("mob_wandering-bandit");
        assertThat(findByCode("stone_ogre").modelKey()).isEqualTo("mob_stone-ogre");
        // Hình nộm là hàng đối chứng của V23 — không đợt nào được lặng lẽ đổi nó.
        assertThat(findByCode("training_dummy").modelKey()).isEqualTo("mob_training-dummy");
    }

    @Test
    void shouldListMainWorldSpawnPointsOnThePlayLine() {
        List<MonsterSpawnPoint> spawns =
                new PostgresMonsterCatalogAdapter(dataSource).listSpawnPoints(MonsterSpawnPoint.MAIN_WORLD_CODE);

        assertThat(spawns).hasSize(5);
        // ADR-033 — quái lệch khỏi y=0 là quái người chơi không bao giờ chạm tới. DB có CHECK,
        // đây là hàng rào thứ hai và cũng là tài liệu sống cho ràng buộc đó.
        assertThat(spawns).allSatisfy(spawn -> assertThat(spawn.posY()).isZero());
        assertThat(spawns)
                .allSatisfy(spawn -> assertThat(spawn.respawnSeconds()).isPositive());
        // Bốn vị trí cũ giữ nguyên; chỉ loài đứng ở đó đổi, cộng thêm quỷ đá ở xa nhất.
        assertThat(spawns).extracting(MonsterSpawnPoint::posX).containsExactly(8f, 14f, 21f, 27f, 34f);
    }

    @Test
    void shouldReturnEmptyForWorldWithoutSpawnPoints() {
        // Dungeon/arena chưa khai điểm spawn nào — rỗng là hợp lệ, không phải lỗi.
        assertThat(new PostgresMonsterCatalogAdapter(dataSource).listSpawnPoints("dungeon"))
                .isEmpty();
    }

    private static MonsterTemplate findByCode(String code) {
        return new PostgresMonsterCatalogAdapter(dataSource)
                .listTemplates().stream()
                        .filter(template -> template.code().equals(code))
                        .findFirst()
                        .orElseThrow();
    }
}
