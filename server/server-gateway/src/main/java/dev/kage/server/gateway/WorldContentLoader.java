package dev.kage.server.gateway;

import java.util.List;

import dev.kage.dialogue.api.NpcPlacementFacade;
import dev.kage.dialogue.domain.NpcPlacement;
import dev.kage.monster.api.MonsterFacade;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterStats;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.world.MonsterCatalogSnapshot;
import dev.kage.server.world.NpcCatalogSnapshot;
import dev.kage.server.world.WorldContentCatalog;

/**
 * Dịch nội dung tĩnh của world — quái ({@code feature-monster}, ADR-037) và NPC
 * ({@code feature-dialogue}, ADR-040) — sang record thuần của {@code server-world}.
 *
 * <p>Cầu nối này phải nằm ở gateway và không nằm chỗ nào khác được: {@code server-world} không được
 * biết DB (Điều 23), còn {@code server-database} thậm chí <b>không phụ thuộc</b>
 * {@code server-world} — chỉ module này phụ thuộc cả hai. Đúng hình dạng đã buộc ADR-036 phải dịch
 * {@code EquipmentBonus} → {@code EquipmentBonusSnapshot} ở đây.
 *
 * <p>Nạp MỘT LẦN lúc khởi động: {@code monster_template}/{@code npc_spawn} là config tĩnh, cùng
 * cách {@code item_template} được đối xử (DATABASE.md §182). Đổi cân bằng hay dời NPC ⇒ migration +
 * khởi động lại, không có đường nóng.
 */
final class WorldContentLoader {

    private WorldContentLoader() {}

    /**
     * Đọc toàn bộ nội dung tĩnh của một world.
     *
     * @param worldCode {@link MonsterSpawnPoint#MAIN_WORLD_CODE} cho world chính; world nào không
     *     khai điểm spawn/NPC nào sẽ nhận catalog rỗng, tức không quái không NPC — đúng hành vi
     *     dungeon/arena. Hai feature module khai cùng chuỗi {@code "main"}
     *     ({@link NpcPlacement#MAIN_WORLD_CODE}) vì đó là CÙNG một world; chỗ gọi truyền một giá
     *     trị duy nhất cho cả hai, và có test khoá hai hằng số đó bằng nhau
     */
    static WorldContentCatalog load(MonsterFacade monsterFacade, NpcPlacementFacade npcFacade, String worldCode) {
        return new WorldContentCatalog(loadMonsters(monsterFacade, worldCode), loadNpcs(npcFacade, worldCode));
    }

    private static MonsterCatalogSnapshot loadMonsters(MonsterFacade monsterFacade, String worldCode) {
        List<MonsterCatalogSnapshot.MonsterSpecies> species = monsterFacade.listTemplates().stream()
                .map(WorldContentLoader::toSpecies)
                .toList();
        List<MonsterCatalogSnapshot.MonsterSpawnDefinition> spawns = monsterFacade.listSpawnPoints(worldCode).stream()
                .map(WorldContentLoader::toSpawnDefinition)
                .toList();
        return MonsterCatalogSnapshot.of(species, spawns);
    }

    private static NpcCatalogSnapshot loadNpcs(NpcPlacementFacade npcFacade, String worldCode) {
        // Không có bước lọc "trỏ tới loài lạ" như quái: npc_spawn.npc_template_id là FK NOT NULL và
        // adapter JOIN thẳng npc_template, nên mọi hàng ra khỏi DB đều có mã NPC có thật.
        return new NpcCatalogSnapshot(npcFacade.listPlacements(worldCode).stream()
                .map(WorldContentLoader::toPlacementDefinition)
                .toList());
    }

    private static MonsterCatalogSnapshot.MonsterSpecies toSpecies(MonsterTemplate template) {
        MonsterStats stats = template.stats();
        MonsterCatalogSnapshot.MonsterCombatNumbers combat = new MonsterCatalogSnapshot.MonsterCombatNumbers(
                stats.atk(),
                stats.skillDmg(),
                stats.def(),
                stats.res(),
                stats.hitPercent(),
                stats.dodgePercent(),
                stats.critPercent(),
                stats.element());
        return new MonsterCatalogSnapshot.MonsterSpecies(
                template.code(), template.modelKey(), stats.maxHp(), template.expReward(), combat);
    }

    private static MonsterCatalogSnapshot.MonsterSpawnDefinition toSpawnDefinition(MonsterSpawnPoint spawn) {
        return new MonsterCatalogSnapshot.MonsterSpawnDefinition(
                spawn.monsterCode(), spawn.posX(), spawn.posY(), spawn.respawnSeconds());
    }

    private static NpcCatalogSnapshot.NpcPlacementDefinition toPlacementDefinition(NpcPlacement placement) {
        return new NpcCatalogSnapshot.NpcPlacementDefinition(placement.npcCode(), placement.posX(), placement.posY());
    }
}
