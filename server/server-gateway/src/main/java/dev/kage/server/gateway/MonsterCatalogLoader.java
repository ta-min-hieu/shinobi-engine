package dev.kage.server.gateway;

import java.util.List;

import dev.kage.monster.api.MonsterFacade;
import dev.kage.monster.domain.MonsterAiParams;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterStats;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.world.MonsterCatalogSnapshot;

/**
 * Dịch danh mục quái từ {@code feature-monster} sang record thuần của {@code server-world}
 * (ADR-037 D3).
 *
 * <p>Cầu nối này phải nằm ở gateway và không nằm chỗ nào khác được: {@code server-world} không được
 * biết DB (Điều 23), còn {@code server-database} thậm chí <b>không phụ thuộc</b>
 * {@code server-world} — chỉ module này phụ thuộc cả hai. Đúng hình dạng đã buộc ADR-036 phải dịch
 * {@code EquipmentBonus} → {@code EquipmentBonusSnapshot} ở đây.
 *
 * <p>Nạp MỘT LẦN lúc khởi động: {@code monster_template} là config tĩnh, cùng cách
 * {@code item_template} được đối xử (DATABASE.md §182). Đổi cân bằng ⇒ migration + khởi động lại,
 * không có đường nóng.
 */
final class MonsterCatalogLoader {

    private MonsterCatalogLoader() {}

    /**
     * Đọc catalog của một world.
     *
     * @param worldCode {@link MonsterSpawnPoint#MAIN_WORLD_CODE} cho world chính; world nào không
     *     khai điểm spawn nào sẽ nhận catalog rỗng, tức không có quái — đúng hành vi dungeon/arena
     */
    static MonsterCatalogSnapshot load(MonsterFacade monsterFacade, String worldCode) {
        List<MonsterCatalogSnapshot.MonsterSpecies> species = monsterFacade.listTemplates().stream()
                .map(MonsterCatalogLoader::toSpecies)
                .toList();
        List<MonsterCatalogSnapshot.MonsterSpawnDefinition> spawns = monsterFacade.listSpawnPoints(worldCode).stream()
                .map(MonsterCatalogLoader::toSpawnDefinition)
                .toList();
        return MonsterCatalogSnapshot.of(species, spawns);
    }

    private static MonsterCatalogSnapshot.MonsterSpecies toSpecies(MonsterTemplate template) {
        MonsterStats stats = template.stats();
        MonsterAiParams ai = template.aiParams();
        MonsterCatalogSnapshot.MonsterAiNumbers aiNumbers = new MonsterCatalogSnapshot.MonsterAiNumbers(
                ai.aggroRadiusMeters(),
                ai.leashRadiusMeters(),
                ai.chaseSpeedMps(),
                ai.patrolSpeedMps(),
                ai.patrolRadiusMeters(),
                ai.idleDurationSeconds());
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
                template.code(), template.modelKey(), stats.maxHp(), template.expReward(), combat, aiNumbers);
    }

    private static MonsterCatalogSnapshot.MonsterSpawnDefinition toSpawnDefinition(MonsterSpawnPoint spawn) {
        return new MonsterCatalogSnapshot.MonsterSpawnDefinition(
                spawn.monsterCode(), spawn.posX(), spawn.posY(), spawn.respawnSeconds());
    }
}
