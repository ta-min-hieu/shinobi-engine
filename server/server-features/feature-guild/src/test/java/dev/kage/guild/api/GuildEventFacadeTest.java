package dev.kage.guild.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-256 — tiêu đề và khoảng giờ hẹn hợp lệ. */
class GuildEventFacadeTest {

    private static final Instant NOW = Instant.parse("2026-11-08T10:00:00Z");

    @Test
    void acceptsOneMinuteToSevenDaysAhead() {
        assertThat(GuildEventFacade.isValid("Đi bí cảnh", NOW.plus(Duration.ofHours(2)), NOW))
                .isTrue();
        assertThat(GuildEventFacade.isValid("Đi bí cảnh", NOW.plusSeconds(30), NOW))
                .isFalse();
        assertThat(GuildEventFacade.isValid("Đi bí cảnh", NOW.plus(Duration.ofDays(8)), NOW))
                .isFalse();
        assertThat(GuildEventFacade.isValid(" ", NOW.plus(Duration.ofHours(2)), NOW))
                .isFalse();
        assertThat(GuildEventFacade.isValid("x".repeat(61), NOW.plus(Duration.ofHours(2)), NOW))
                .isFalse();
    }
}
