package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EnhancementTableTest {

    @Test
    void shouldReturnDecreasingSuccessRate_asLevelIncreases() {
        int previous = 101;
        for (int level = 0; level < ItemStack.MAX_ENHANCE; level++) {
            int rate = EnhancementTable.successRatePercent(level);
            assertThat(rate).isLessThan(previous).isBetween(1, 100);
            previous = rate;
        }
    }

    @Test
    void shouldReturnIncreasingGoldCost_asLevelIncreases() {
        long previous = -1;
        for (int level = 0; level < ItemStack.MAX_ENHANCE; level++) {
            long cost = EnhancementTable.goldCost(level);
            assertThat(cost).isGreaterThan(previous);
            previous = cost;
        }
    }

    @Test
    void shouldMatchDropOnFailThreshold_withGdd() {
        assertThat(EnhancementTable.DROP_ON_FAIL_FROM_LEVEL).isEqualTo(10);
    }
}
