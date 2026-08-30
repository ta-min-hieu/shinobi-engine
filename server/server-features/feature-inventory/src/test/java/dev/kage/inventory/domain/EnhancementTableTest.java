package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldReturnBaseBonus_whenNotEnhanced() {
        assertThat(EnhancementTable.enhancedBonus(18, 0)).isEqualTo(18);
    }

    @Test
    void shouldScaleBonusByTenPercentPerLevel() {
        // ADR-036 D3 — bonus_gốc × (1 + 0,10 × cấp). 20 ở +5 ⇒ ×1,5 ⇒ 30; ở +15 ⇒ ×2,5 ⇒ 50.
        assertThat(EnhancementTable.enhancedBonus(20, 5)).isEqualTo(30);
        assertThat(EnhancementTable.enhancedBonus(20, ItemStack.MAX_ENHANCE)).isEqualTo(50);
    }

    @Test
    void shouldKeepZeroAtZero_soEnhancementAmplifiesNeverCreates() {
        // Cường hoá một chiếc nhẫn không cộng DEF KHÔNG được đẻ ra DEF từ hư vô, dù cường hoá tới
        // +15 — nếu không, mọi món đều thành món đa chỉ số khi lên cấp cao.
        assertThat(EnhancementTable.enhancedBonus(0, ItemStack.MAX_ENHANCE)).isZero();
    }

    @Test
    void shouldGrowMonotonically_soEnhancingIsNeverAWaste() {
        int previous = -1;
        for (int level = 0; level <= ItemStack.MAX_ENHANCE; level++) {
            int bonus = EnhancementTable.enhancedBonus(10, level);
            assertThat(bonus).isGreaterThan(previous);
            previous = bonus;
        }
    }

    @Test
    void shouldRejectEnhanceLevelOutsideRange() {
        assertThatThrownBy(() -> EnhancementTable.enhancedBonus(10, -1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EnhancementTable.enhancedBonus(10, ItemStack.MAX_ENHANCE + 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
