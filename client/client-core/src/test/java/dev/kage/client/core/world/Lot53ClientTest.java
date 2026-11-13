package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.ZonedDateTime;

import org.junit.jupiter.api.Test;

/** Lô 53 phía client — đếm ngược reset ngày (ADR-356). */
class Lot53ClientTest {

    @Test
    void nextResetIsVietnamMidnight() {
        long now = ZonedDateTime.of(2026, 11, 13, 23, 30, 0, 0, ResetCountdownCoordinator.RESET_ZONE)
                .toInstant()
                .toEpochMilli();
        long next = ResetCountdownCoordinator.nextResetMillis(now);
        assertThat(next - now).isEqualTo(30L * 60L * 1000L);
        assertThat(ResetCountdownCoordinator.nextResetMillis(next)).isEqualTo(next + 24L * 3600L * 1000L);
    }
}
