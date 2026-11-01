package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Biên dạng hậu cảnh (lô 25) — tính một lần lúc dựng; đây là phần thuần số, không cần GL. */
class HorizonBackdropTest {

    @Test
    void shouldKeepEverySpireSampleInsideTheUnitRange_andRaiseRealPeaks() {
        float[] profile = HorizonBackdrop.spireProfile(7, 0f);

        float highest = 0f;
        for (float sample : profile) {
            assertThat(sample).isBetween(0f, 1f);
            highest = Math.max(highest, sample);
        }
        // Có đỉnh thật (không phải dải phẳng) mà cũng có thung lũng chạm đáy giữa các đỉnh.
        assertThat(highest).isGreaterThan(0.4f);
        assertThat(profile).contains(0f);
    }

    @Test
    void shouldGiveTheNearLayerADifferentSkyline_thanTheFarLayer() {
        assertThat(HorizonBackdrop.spireProfile(9, 0.5f)).isNotEqualTo(HorizonBackdrop.spireProfile(7, 0f));
    }

    @Test
    void shouldNeverLetTheCloudCrestSinkToTheBase() {
        for (float sample : HorizonBackdrop.cloudProfile()) {
            assertThat(sample).isBetween(0.15f, 1f);
        }
    }
}
