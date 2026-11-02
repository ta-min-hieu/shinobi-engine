package dev.kage.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** ADR-149 — ngày theo giờ Việt Nam, phần trăm EXP làm tròn xuống, không âm. */
class FortuneRulesTest {

    @Test
    void dayShouldFollowVietnamTime() {
        // 17:30 UTC ngày 1 = 00:30 sáng ngày 2 giờ VN.
        assertThat(FortuneRules.day(Instant.parse("2026-11-01T17:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(FortuneRules.day(Instant.parse("2026-11-01T16:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 1));
    }

    @Test
    void boostShouldScaleAndRoundDown() {
        assertThat(FortuneRules.boosted(100, 20)).isEqualTo(120);
        assertThat(FortuneRules.boosted(35, -10)).isEqualTo(31);
        assertThat(FortuneRules.boosted(7, 5)).isEqualTo(7);
        assertThat(FortuneRules.boosted(100, 0)).isEqualTo(100);
        assertThat(FortuneRules.boosted(0, 20)).isZero();
    }
}
