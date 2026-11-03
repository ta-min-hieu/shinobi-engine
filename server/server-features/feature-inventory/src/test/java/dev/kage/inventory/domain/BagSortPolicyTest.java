package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.assertj.core.groups.Tuple;
import org.junit.jupiter.api.Test;

final class BagSortPolicyTest {

    private static final long PILL = 20L;
    private static final long HERB = 30L;
    private static final long SWORD = 2L;
    private static final long HELMET = 5L;
    private static final int PILL_STACK = 10;

    @Test
    void mergesStacksOfTheSameItemIntoTheOldestRowsUpToMaxStack() {
        BagSortPolicy.Plan plan = BagSortPolicy.plan(List.of(pill(1L, 7), pill(4L, 6), pill(9L, 2)));

        assertThat(plan.placements())
                .extracting(BagSortPolicy.Placement::id, BagSortPolicy.Placement::quantity)
                .containsExactly(Tuple.tuple(1L, 10), Tuple.tuple(4L, 5));
        assertThat(plan.deletedIds()).containsExactly(9L);
    }

    @Test
    void lockedAndUnlockedStacksAreNeverMergedTogether() {
        BagSortPolicy.Row locked =
                new BagSortPolicy.Row(3L, PILL, "pill", 2, PILL_STACK, Optional.empty(), true, 1, 0, false, true, true);

        BagSortPolicy.Plan plan = BagSortPolicy.plan(List.of(pill(1L, 2), locked));

        assertThat(plan.deletedIds()).isEmpty();
        assertThat(plan.placements()).hasSize(2);
    }

    @Test
    void ordersEquipmentThenUsableThenOtherThenEquipped() {
        BagSortPolicy.Row herb =
                new BagSortPolicy.Row(1L, HERB, "herb", 3, 99, Optional.empty(), false, 1, 0, false, true, false);
        BagSortPolicy.Row wornSword =
                new BagSortPolicy.Row(2L, SWORD, "sword", 1, 1, Optional.of("WEAPON"), false, 1, 0, true, false, false);
        BagSortPolicy.Row helmet = new BagSortPolicy.Row(
                3L, HELMET, "helmet", 1, 1, Optional.of("HELMET"), false, 5, 0, false, true, false);
        BagSortPolicy.Row sword = new BagSortPolicy.Row(
                4L, SWORD, "sword", 1, 1, Optional.of("WEAPON"), false, 1, 3, false, false, false);

        BagSortPolicy.Plan plan = BagSortPolicy.plan(List.of(herb, wornSword, helmet, pill(5L, 1), sword));

        assertThat(plan.placements()).extracting(BagSortPolicy.Placement::id).containsExactly(4L, 3L, 5L, 1L, 2L);
        assertThat(plan.placements())
                .extracting(BagSortPolicy.Placement::slotIndex)
                .containsExactly(0, 1, 2, 3, 4);
    }

    @Test
    void nonMergeableRowsKeepTheirQuantity() {
        BagSortPolicy.Row enhanced = new BagSortPolicy.Row(
                1L, PILL, "pill", 4, PILL_STACK, Optional.empty(), true, 1, 2, false, false, false);

        BagSortPolicy.Plan plan = BagSortPolicy.plan(List.of(enhanced, pill(2L, 9)));

        assertThat(plan.deletedIds()).isEmpty();
        assertThat(plan.placements())
                .extracting(BagSortPolicy.Placement::quantity)
                .containsExactlyInAnyOrder(4, 9);
    }

    private static BagSortPolicy.Row pill(long id, int quantity) {
        return new BagSortPolicy.Row(
                id, PILL, "pill", quantity, PILL_STACK, Optional.empty(), true, 1, 0, false, true, false);
    }
}
