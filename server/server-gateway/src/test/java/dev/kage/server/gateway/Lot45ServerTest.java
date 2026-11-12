package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** Lô 45 — quà mốc gắn bó (ADR-289): mốc, quà, khoá chống trùng. */
class Lot45ServerTest {

    private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void countsLoyaltyMilestones() {
        assertThat(Lot45Commands.milestone(CREATED, CREATED.plus(Duration.ofDays(29))))
                .isZero();
        assertThat(Lot45Commands.milestone(CREATED, CREATED.plus(Duration.ofDays(30))))
                .isEqualTo(1);
        assertThat(Lot45Commands.milestone(CREATED, CREATED.plus(Duration.ofDays(95))))
                .isEqualTo(3);
        assertThat(Lot45Commands.milestone(CREATED, CREATED.minusSeconds(5))).isZero();
    }

    @Test
    void giftGrowsThenCaps() {
        assertThat(Lot45Commands.loyaltyGold(1)).isEqualTo(1_000L);
        assertThat(Lot45Commands.loyaltyGold(12)).isEqualTo(12_000L);
        assertThat(Lot45Commands.loyaltyGold(40)).isEqualTo(12_000L);
    }

    @Test
    void keyIsStablePerCharacterAndMilestone() {
        assertThat(Lot45Commands.loyaltyKey(2, 1)).isEqualTo(Lot45Commands.loyaltyKey(2, 1));
        assertThat(Lot45Commands.loyaltyKey(2, 1)).isNotEqualTo(Lot45Commands.loyaltyKey(2, 2));
        assertThat(Lot45Commands.loyaltyKey(2, 1)).isNotEqualTo(Lot45Commands.loyaltyKey(3, 1));
        assertThat(Lot45Commands.loyaltyBody(2)).contains("60 ngày").contains("60 days");
    }
}
