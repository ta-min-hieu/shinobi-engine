package dev.kage.character.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-095 — quỹ kẹp 8 giờ, 8 giờ ≈ một cấp, phí x2 theo cấp. */
class SeclusionPolicyTest {

    private static final Instant T0 = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void shouldClampOfflineMinutesAndTheBank() {
        assertThat(SeclusionPolicy.offlineMinutes(T0, T0.plus(Duration.ofSeconds(119))))
                .isEqualTo(1);
        assertThat(SeclusionPolicy.offlineMinutes(T0, T0.plus(Duration.ofDays(3))))
                .isEqualTo(480);
        assertThat(SeclusionPolicy.offlineMinutes(T0, T0.minusSeconds(60))).isZero();
        assertThat(SeclusionPolicy.addToBank(400, 200)).isEqualTo(480);
    }

    @Test
    void eightHoursShouldBeWorthAboutOneLevel() {
        long level41 = ExperienceCurve.expForNextLevel(41);
        assertThat(SeclusionPolicy.expFor(480, 41)).isBetween(level41 - 480, level41);
        assertThat(SeclusionPolicy.expFor(480, 1)).isEqualTo(480L); // tối thiểu 1 EXP/phút
        assertThat(SeclusionPolicy.expFor(30, ExperienceCurve.MAX_LEVEL)).isEqualTo(30L);
        assertThat(SeclusionPolicy.doubleCost(41)).isEqualTo(2_050L);
    }
}
