package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-124 — tỉ lệ thu phục giảm 5% mỗi cấp quái hơn người, sàn 5%. */
class TamingRulesTest {

    @Test
    void shouldKeepBaseChanceWhenNotOutleveled() {
        assertThat(TamingRules.chancePercent(40, 6, 6)).isEqualTo(40);
        assertThat(TamingRules.chancePercent(40, 6, 30)).isEqualTo(40);
    }

    @Test
    void shouldLoseFivePercentPerLevelGapDownToTheFloor() {
        assertThat(TamingRules.chancePercent(40, 29, 25)).isEqualTo(20);
        assertThat(TamingRules.chancePercent(40, 29, 10)).isEqualTo(5);
        assertThat(TamingRules.chancePercent(3, 29, 10))
                .as("gốc dưới sàn thì giữ gốc")
                .isEqualTo(3);
    }
}
