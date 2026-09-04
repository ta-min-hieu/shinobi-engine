package dev.kage.server.database.dialogue;

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

import dev.kage.dialogue.domain.NpcPlacement;
import dev.kage.server.database.DatabaseConfig;
import dev.kage.server.database.FlywayMigrator;
import dev.kage.server.database.PostgresDataSourceFactory;

/**
 * Test bằng PostgreSQL THẬT qua Testcontainers — verify migration V26 ({@code npc_spawn}) +
 * {@link PostgresNpcPlacementAdapter} (ADR-040).
 *
 * <p>Thước đo quan trọng nhất ở đây là {@link #shouldSeedTownElder_atExactlyTheOldHardcodedSpot()}:
 * ADR-040 D4 đặt ràng buộc "di trú KHÔNG đổi hành vi", nên seed phải khớp TỪNG SỐ với ba hằng số cũ
 * {@code WorldFacade.SAMPLE_NPC_CODE}/{@code SAMPLE_NPC_SPAWN_X}/{@code SAMPLE_NPC_SPAWN_Y}. Một
 * test đỏ ở đây nghĩa là trưởng lão vừa đổi chỗ mà không ai định làm thế.
 */
@Testcontainers
class NpcPlacementIntegrationTest {

    private static final int POSTGRES_PORT = 5432;

    /** Nguyên văn {@code WorldFacade.SAMPLE_NPC_SPAWN_X} trước ADR-040. */
    private static final float OLD_HARDCODED_X = -5f;

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
    void shouldSeedTownElder_atExactlyTheOldHardcodedSpot() {
        List<NpcPlacement> placements =
                new PostgresNpcPlacementAdapter(dataSource).listPlacements(NpcPlacement.MAIN_WORLD_CODE);

        assertThat(placements).hasSize(1);
        NpcPlacement elder = placements.get(0);
        assertThat(elder.npcCode()).isEqualTo("town_elder");
        assertThat(elder.posX()).isEqualTo(OLD_HARDCODED_X);
        // ADR-033: người chơi luôn ở y=0, nên NPC lệch y là NPC không bao giờ nói chuyện được.
        assertThat(elder.posY()).isZero();
        assertThat(elder.worldCode()).isEqualTo(NpcPlacement.MAIN_WORLD_CODE);
    }

    @Test
    void shouldNotSeedShopKeeper_becauseItHasNoDialogueScriptYet() {
        // ADR-040 D7 để `shop_keeper` sang đợt riêng có chủ đích. Seed nó mà chưa có
        // `shop_keeper.js` sẽ làm ScriptValidator chặn server ngay lúc khởi động (SCRIPTING.md §8),
        // chứ không phải chỉ "thừa một NPC" — nên đây là test gác, không phải test thừa.
        List<NpcPlacement> placements =
                new PostgresNpcPlacementAdapter(dataSource).listPlacements(NpcPlacement.MAIN_WORLD_CODE);

        assertThat(placements).extracting(NpcPlacement::npcCode).doesNotContain("shop_keeper");
    }

    @Test
    void shouldReturnEmpty_forWorldWithoutPlacements() {
        // Dungeon/arena không đặt NPC nào — rỗng là hợp lệ, KHÔNG phải lỗi.
        List<NpcPlacement> placements = new PostgresNpcPlacementAdapter(dataSource).listPlacements("dungeon");

        assertThat(placements).isEmpty();
    }
}
