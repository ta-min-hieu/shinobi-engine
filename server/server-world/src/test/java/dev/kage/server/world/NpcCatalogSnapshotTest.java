package dev.kage.server.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

/** ADR-040 — record thuần world nhận từ gateway. */
class NpcCatalogSnapshotTest {

    @Test
    void shouldBeEmpty_forDungeonAndArena() {
        assertThat(NpcCatalogSnapshot.EMPTY.isEmpty()).isTrue();
        assertThat(NpcCatalogSnapshot.EMPTY.placements()).isEmpty();
    }

    @Test
    void shouldKeepPlacementOrder() {
        // Không ai dựa vào thứ tự spawn, nhưng thứ tự ngẫu nhiên làm test khó tái hiện mà chẳng đổi
        // lấy gì — adapter đã ORDER BY ns.id, record giữ nguyên.
        NpcCatalogSnapshot snapshot = new NpcCatalogSnapshot(List.of(
                new NpcCatalogSnapshot.NpcPlacementDefinition("town_elder", -5f, 0f),
                new NpcCatalogSnapshot.NpcPlacementDefinition("shop_keeper", 12f, 0f)));

        assertThat(snapshot.placements())
                .extracting(NpcCatalogSnapshot.NpcPlacementDefinition::npcCode)
                .containsExactly("town_elder", "shop_keeper");
    }
}
