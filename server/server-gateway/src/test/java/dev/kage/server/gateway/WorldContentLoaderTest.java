package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.dialogue.api.NpcPlacementFacade;
import dev.kage.dialogue.domain.NpcPlacement;
import dev.kage.dialogue.domain.NpcPlacementPort;
import dev.kage.monster.api.MonsterFacade;
import dev.kage.monster.domain.MonsterAiParams;
import dev.kage.monster.domain.MonsterCatalogPort;
import dev.kage.monster.domain.MonsterSpawnPoint;
import dev.kage.monster.domain.MonsterStats;
import dev.kage.monster.domain.MonsterTemplate;
import dev.kage.server.world.SafeZoneCatalogSnapshot;
import dev.kage.server.world.WorldContentCatalog;
import dev.kage.world.api.WorldGeometryFacade;
import dev.kage.world.domain.SafeZone;
import dev.kage.world.domain.SafeZonePort;

/**
 * ADR-040 D6 (+ ADR-046 D3) — gateway là chỗ DUY NHẤT dịch được nội dung tĩnh từ feature module
 * sang record thuần của {@code server-world} (nó là module duy nhất phụ thuộc cả hai bên).
 */
class WorldContentLoaderTest {

    private static MonsterFacade monsterFacadeWith(List<MonsterSpawnPoint> spawns) {
        return new MonsterFacade(new MonsterCatalogPort() {
            @Override
            public List<MonsterTemplate> listTemplates() {
                return List.of(new MonsterTemplate(
                        "training_dummy",
                        "hud.monster.training_dummy",
                        "mob_training-dummy",
                        new MonsterStats(1, 50, 8, 0, 5, 3, 90, 0, 0, "NONE"),
                        // Đúng số V25 seed cho cọc tập: thụ động (ADR-039 D4).
                        new MonsterAiParams(0f, 10f, 3f, 1.5f, 0f, 3f),
                        20L,
                        null));
            }

            @Override
            public List<MonsterSpawnPoint> listSpawnPoints(String worldCode) {
                return List.copyOf(spawns); // copy: adapter thật cũng trả list mới mỗi lần query
            }
        });
    }

    private static NpcPlacementFacade npcFacadeWith(List<NpcPlacement> placements) {
        NpcPlacementPort port = worldCode -> placements;
        return new NpcPlacementFacade(port);
    }

    private static WorldGeometryFacade geometryFacadeWith(List<SafeZone> zones) {
        SafeZonePort port = worldCode -> zones;
        return new WorldGeometryFacade(port);
    }

    @Test
    void shouldCarryNpcCodeAndPositionThrough_unchanged() {
        WorldContentCatalog content = WorldContentLoader.load(
                monsterFacadeWith(List.of()),
                npcFacadeWith(List.of(new NpcPlacement("town_elder", NpcPlacement.MAIN_WORLD_CODE, -5f, 0f))),
                geometryFacadeWith(List.of()),
                MonsterSpawnPoint.MAIN_WORLD_CODE);

        assertThat(content.npcs().placements()).hasSize(1);
        assertThat(content.npcs().placements().get(0).npcCode()).isEqualTo("town_elder");
        assertThat(content.npcs().placements().get(0).posX()).isEqualTo(-5f);
        assertThat(content.npcs().placements().get(0).posY()).isZero();
    }

    @Test
    void shouldLoadBothCatalogs_inOneCall() {
        WorldContentCatalog content = WorldContentLoader.load(
                monsterFacadeWith(List.of(
                        new MonsterSpawnPoint("training_dummy", MonsterSpawnPoint.MAIN_WORLD_CODE, 8f, 0f, 10f))),
                npcFacadeWith(List.of(new NpcPlacement("town_elder", NpcPlacement.MAIN_WORLD_CODE, -5f, 0f))),
                geometryFacadeWith(List.of()),
                MonsterSpawnPoint.MAIN_WORLD_CODE);

        assertThat(content.monsters().spawnPoints()).hasSize(1);
        assertThat(content.npcs().placements()).hasSize(1);
    }

    @Test
    void shouldGiveEmptyNpcCatalog_whenWorldPlacesNone() {
        WorldContentCatalog content = WorldContentLoader.load(
                monsterFacadeWith(List.of()), npcFacadeWith(List.of()), geometryFacadeWith(List.of()), "dungeon");

        assertThat(content.npcs().isEmpty()).isTrue();
        assertThat(content.monsters().isEmpty()).isTrue();
        assertThat(content.safeZones().isEmpty()).isTrue();
    }

    /** ADR-046 D3 — biên vùng an toàn phải đi qua nguyên vẹn: lệch một mét là hồi máu sai chỗ. */
    @Test
    void shouldCarrySafeZoneBoundsThrough_unchanged() {
        WorldContentCatalog content = WorldContentLoader.load(
                monsterFacadeWith(List.of()),
                npcFacadeWith(List.of()),
                geometryFacadeWith(
                        List.of(new SafeZone("sect_grounds", SafeZone.MAIN_WORLD_CODE, -15f, -10f, 5f, 10f))),
                MonsterSpawnPoint.MAIN_WORLD_CODE);

        assertThat(content.safeZones().zones())
                .extracting(
                        SafeZoneCatalogSnapshot.SafeZoneDefinition::code,
                        SafeZoneCatalogSnapshot.SafeZoneDefinition::minX,
                        SafeZoneCatalogSnapshot.SafeZoneDefinition::minY,
                        SafeZoneCatalogSnapshot.SafeZoneDefinition::maxX,
                        SafeZoneCatalogSnapshot.SafeZoneDefinition::maxY)
                .containsExactly(tuple("sect_grounds", -15f, -10f, 5f, 10f));
    }

    @Test
    void shouldAgreeOnMainWorldCode_acrossBothFeatureModules() {
        // WorldContentLoader.load nhận MỘT worldCode cho cả quái lẫn NPC vì đó là CÙNG một world.
        // Hai module khai hằng số riêng (không module nào phụ thuộc module kia); lệch nhau thì
        // world chính sẽ có quái mà không có NPC, hoặc ngược lại, và không có gì báo. Khoá lại.
        assertThat(NpcPlacement.MAIN_WORLD_CODE).isEqualTo(MonsterSpawnPoint.MAIN_WORLD_CODE);
        assertThat(SafeZone.MAIN_WORLD_CODE).isEqualTo(MonsterSpawnPoint.MAIN_WORLD_CODE);
    }
}
