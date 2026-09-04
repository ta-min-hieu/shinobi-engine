package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-040 D6 — gói quái + NPC vào một tham số để không vượt trần Checkstyle 4 tham số. */
class WorldContentCatalogTest {

    @Test
    void shouldExposeBothCatalogsEmpty_forDungeonAndArena() {
        assertThat(WorldContentCatalog.EMPTY.monsters().isEmpty()).isTrue();
        assertThat(WorldContentCatalog.EMPTY.npcs().isEmpty()).isTrue();
    }

    @Test
    void shouldDefaultToNoNpcs_whenBuiltFromMonstersOnly() {
        WorldContentCatalog content = WorldContentCatalog.ofMonsters(TestMonsterCatalogs.trainingGround());

        assertThat(content.monsters().isEmpty()).isFalse();
        assertThat(content.npcs().isEmpty()).isTrue();
    }

    @Test
    void shouldRejectNull_soCallersUseEmptyInstead() {
        // null ở đây sẽ nổ muộn trong tick thread (nơi 1 exception từng đóng băng cả world) thay vì
        // nổ ngay lúc dựng — nên chặn ở constructor.
        assertThatThrownBy(() -> new WorldContentCatalog(null, NpcCatalogSnapshot.EMPTY))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new WorldContentCatalog(MonsterCatalogSnapshot.EMPTY, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCopyNpcPlacements_soLaterMutationCannotChangeAWorldAlreadyBuilt() {
        List<NpcCatalogSnapshot.NpcPlacementDefinition> mutable = new java.util.ArrayList<>();
        mutable.add(new NpcCatalogSnapshot.NpcPlacementDefinition("town_elder", -5f, 0f));
        NpcCatalogSnapshot snapshot = new NpcCatalogSnapshot(mutable);

        mutable.add(new NpcCatalogSnapshot.NpcPlacementDefinition("shop_keeper", 12f, 0f));

        assertThat(snapshot.placements()).hasSize(1);
    }
}
