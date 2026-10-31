package dev.kage.crafting.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.SplittableRandom;

import org.junit.jupiter.api.Test;

/** ADR-120 D3 — phẩm chất 70/25/5 ⇒ 0/1/2 dòng tẩy luyện. */
class ForgeQualityTest {

    @Test
    void shouldMapPercentileBoundaries() {
        assertThat(ForgeQuality.fromPercentile(0)).isEqualTo(ForgeQuality.COMMON);
        assertThat(ForgeQuality.fromPercentile(69)).isEqualTo(ForgeQuality.COMMON);
        assertThat(ForgeQuality.fromPercentile(70)).isEqualTo(ForgeQuality.FINE);
        assertThat(ForgeQuality.fromPercentile(94)).isEqualTo(ForgeQuality.FINE);
        assertThat(ForgeQuality.fromPercentile(95)).isEqualTo(ForgeQuality.RARE);
        assertThat(ForgeQuality.fromPercentile(99)).isEqualTo(ForgeQuality.RARE);
    }

    @Test
    void shouldSeedZeroOneOrTwoLines() {
        assertThat(ForgeQuality.COMMON.refinementLines()).isZero();
        assertThat(ForgeQuality.FINE.refinementLines()).isEqualTo(1);
        assertThat(ForgeQuality.RARE.refinementLines()).isEqualTo(2);
    }

    @Test
    void shouldRollRoughlySeventyTwentyFiveFive() {
        SplittableRandom random = new SplittableRandom(42);
        Map<ForgeQuality, Integer> counts = new EnumMap<>(ForgeQuality.class);
        int rolls = 100_000;
        for (int i = 0; i < rolls; i++) {
            counts.merge(ForgeQuality.roll(random), 1, Integer::sum);
        }
        assertThat(counts.get(ForgeQuality.COMMON)).isBetween(69_000, 71_000);
        assertThat(counts.get(ForgeQuality.FINE)).isBetween(24_000, 26_000);
        assertThat(counts.get(ForgeQuality.RARE)).isBetween(4_500, 5_500);
    }
}
