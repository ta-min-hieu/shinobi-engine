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

    /**
     * ⚠️ Test này TỪNG khẳng định {@code hasSize(1)} — "thế giới có đúng một NPC". Đó không phải thứ
     * nó muốn gác (ADR-040 D4 gác việc ông lão không bị XÊ DỊCH), mà chỉ là mô tả tình cờ đúng suốt
     * từ Phase 6 tới V37. NPC thứ hai (`senior_sister`) làm nó đỏ, và đỏ vì một lý do sai. Nay nó
     * lọc ra đúng ông lão rồi kiểm ông lão.
     */
    @Test
    void shouldSeedTownElder_atExactlyTheOldHardcodedSpot() {
        List<NpcPlacement> placements =
                new PostgresNpcPlacementAdapter(dataSource).listPlacements(NpcPlacement.MAIN_WORLD_CODE);

        assertThat(placements)
                .filteredOn(npc -> "town_elder".equals(npc.npcCode()))
                .singleElement()
                .satisfies(elder -> {
                    assertThat(elder.posX()).isEqualTo(OLD_HARDCODED_X);
                    // ADR-033: người chơi luôn ở y=0, nên NPC lệch y là NPC không bao giờ nói chuyện được.
                    assertThat(elder.posY()).isZero();
                    assertThat(elder.worldCode()).isEqualTo(NpcPlacement.MAIN_WORLD_CODE);
                });
    }

    /**
     * NPC THỨ HAI (V37) — Tô Vân Nhi, mở arc 2. x=2 không phải con số tuỳ ý: nó phải thoả BỐN ràng
     * buộc (V37 §2) — trong vùng an toàn {@code sect_grounds} ([-15, 5], V35), ngoài tầm với của
     * thạch vệ @ -20 (luật V32), cách xa hàng xóm phía đông, và **không đứng trong một bệ nhảy
     * demo**.
     *
     * <p>Hai phương án đã bị bác, cả hai đều "trông thì ổn": **x=-9** (chỉ cách thạch vệ 11, trượt
     * luật V32) và **x=4** (nằm GIỮA bệ nhảy [3,7] — trong client thật cô bị khối bệ vẽ đè tới cổ).
     * Ràng buộc thứ tư có hàng rào riêng ở {@code SpawnGeometryIntegrationTest}; ô này chỉ ghim con
     * số, để một lần "dịch cho gần ông lão" sau này phải đọc lý do.
     */
    @Test
    void shouldSeedSeniorSister_insideTheSafeZoneAndOutOfWardenReach() {
        List<NpcPlacement> placements =
                new PostgresNpcPlacementAdapter(dataSource).listPlacements(NpcPlacement.MAIN_WORLD_CODE);

        assertThat(placements)
                .filteredOn(npc -> "senior_sister".equals(npc.npcCode()))
                .singleElement()
                .satisfies(sister -> {
                    assertThat(sister.posX()).isEqualTo(2f);
                    assertThat(sister.posY()).isZero();
                    assertThat(sister.worldCode()).isEqualTo(NpcPlacement.MAIN_WORLD_CODE);
                });
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
