package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ItemSetsTest {

    private static final ItemSet NOVICE = new ItemSet(
            "item_set.novice.name",
            Set.of(1L, 2L, 3L, 4L),
            List.of(
                    new ItemSetTier(2, new EquipmentBonus(0, 0, 0, 5)),
                    new ItemSetTier(4, new EquipmentBonus(1, 2, 2, 0))));

    @Test
    void tiersStack_whenEnoughDistinctPiecesAreWorn() {
        List<ItemSetProgress> progress = ItemSets.progress(List.of(NOVICE), Set.of(1L, 2L, 3L, 4L, 99L));

        assertThat(progress).singleElement().satisfies(set -> {
            assertThat(set.wornPieces()).isEqualTo(4);
            assertThat(set.totalPieces()).isEqualTo(4);
            assertThat(set.activeBonus()).isEqualTo(new EquipmentBonus(1, 2, 2, 5));
        });
    }

    @Test
    void onePieceIsListedButGivesNothing() {
        List<ItemSetProgress> progress = ItemSets.progress(List.of(NOVICE), Set.of(3L));

        assertThat(progress).singleElement().satisfies(set -> {
            assertThat(set.wornPieces()).isEqualTo(1);
            assertThat(set.activeBonus()).isEqualTo(EquipmentBonus.NONE);
        });
        assertThat(ItemSets.totalBonus(progress)).isEqualTo(EquipmentBonus.NONE);
    }

    @Test
    void setsWithNothingWornAreLeftOut() {
        assertThat(ItemSets.progress(List.of(NOVICE), Set.of(99L))).isEmpty();
    }

    @Test
    void totalBonusAddsEverySet() {
        ItemSet other = new ItemSet(
                "item_set.genin.name", Set.of(10L, 11L), List.of(new ItemSetTier(2, new EquipmentBonus(3, 0, 0, 0))));

        List<ItemSetProgress> progress = ItemSets.progress(List.of(NOVICE, other), Set.of(1L, 2L, 10L, 11L));

        assertThat(ItemSets.totalBonus(progress)).isEqualTo(new EquipmentBonus(3, 0, 0, 5));
    }
}
