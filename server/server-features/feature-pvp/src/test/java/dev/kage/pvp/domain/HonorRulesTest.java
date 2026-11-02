package dev.kage.pvp.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** ADR-155 — trần điểm ngày, tuần giờ Việt Nam. */
class HonorRulesTest {

    @Test
    void shouldCapDailyEarnings() {
        assertThat(HonorRules.capped(0, HonorRules.WIN_POINTS)).isEqualTo(10);
        assertThat(HonorRules.capped(95, HonorRules.WIN_POINTS)).isEqualTo(5);
        assertThat(HonorRules.capped(100, HonorRules.LOSS_POINTS)).isZero();
    }

    @Test
    void weekShouldStartOnMondayVietnamTime() {
        assertThat(HonorRules.weekStart(Instant.parse("2026-11-08T16:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(HonorRules.weekStart(Instant.parse("2026-11-08T17:30:00Z"))).isEqualTo(LocalDate.of(2026, 11, 9));
    }
}
