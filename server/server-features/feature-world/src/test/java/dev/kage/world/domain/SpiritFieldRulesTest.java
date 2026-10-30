package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SpiritFieldRulesTest {

    @Test
    @DisplayName("Không tưới ⇒ đúng sản lượng gốc")
    void unwateredYieldIsTheBase() {
        assertThat(SpiritFieldRules.harvestYield(3, false, new SplittableRandom(1)))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("Được tưới ⇒ trung bình đúng +10%, mỗi lần chỉ là gốc hoặc gốc+1 (gốc nhỏ)")
    void wateredYieldAveragesTenPercentMore() {
        SplittableRandom random = new SplittableRandom(42);
        long total = 0;
        int runs = 20_000;
        for (int i = 0; i < runs; i++) {
            int yield = SpiritFieldRules.harvestYield(3, true, random);
            assertThat(yield).isBetween(3, 4);
            total += yield;
        }
        assertThat(total / (double) runs).isBetween(3.27, 3.33);
        assertThat(SpiritFieldRules.harvestYield(20, true, random)).isEqualTo(22);
    }

    @Test
    @DisplayName("Giá mở ô tăng dần: ô thứ 5 = 1000, ô thứ 8 = 2500")
    void unlockCostGrows() {
        assertThat(SpiritFieldRules.unlockCost(4)).isEqualTo(1_000L);
        assertThat(SpiritFieldRules.unlockCost(7)).isEqualTo(2_500L);
    }
}
