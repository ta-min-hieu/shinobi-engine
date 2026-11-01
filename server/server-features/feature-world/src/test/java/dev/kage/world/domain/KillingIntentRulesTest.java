package dev.kage.world.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-142 — luật sát khí thuần: phân rã, trần, ngưỡng Ma Đầu, vàng rơi. */
class KillingIntentRulesTest {

    private static final Instant T0 = Instant.parse("2026-11-01T10:00:00Z");

    @Test
    void shouldFadeOnePointPerHalfHour_neverBelowZero() {
        assertThat(KillingIntentRules.effective(3, T0, T0.plusSeconds(29 * 60))).isEqualTo(3);
        assertThat(KillingIntentRules.effective(3, T0, T0.plusSeconds(30 * 60))).isEqualTo(2);
        assertThat(KillingIntentRules.effective(3, T0, T0.plusSeconds(10 * 3600)))
                .isZero();
        assertThat(KillingIntentRules.effective(3, T0, T0.minusSeconds(60))).isEqualTo(3);
    }

    @Test
    void shouldCapAtTenPoints() {
        assertThat(KillingIntentRules.afterKill(KillingIntentRules.MAX_POINTS))
                .isEqualTo(KillingIntentRules.MAX_POINTS);
        assertThat(KillingIntentRules.afterKill(0)).isEqualTo(1);
    }

    @Test
    void shouldMarkAnOutlawFromThreePoints() {
        assertThat(KillingIntentRules.isOutlaw(2)).isFalse();
        assertThat(KillingIntentRules.isOutlaw(3)).isTrue();
    }

    @Test
    void shouldDropTenPercentOfGold_cappedAtFiveThousand() {
        assertThat(KillingIntentRules.goldDrop(8_000L)).isEqualTo(800L);
        assertThat(KillingIntentRules.goldDrop(1_000_000L)).isEqualTo(KillingIntentRules.GOLD_DROP_CAP);
        assertThat(KillingIntentRules.goldDrop(5L)).isZero();
        assertThat(KillingIntentRules.goldDrop(-10L)).isZero();
    }
}
