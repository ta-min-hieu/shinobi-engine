package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Lô 41 — giờ hẹn lịch bang (ADR-256). */
class Lot41CoordinatorsTest {

    private static final Instant NOW = Instant.parse("2026-11-08T13:30:00Z"); // 20:30 giờ +7

    @Test
    void schedulesTodayOrTomorrow() {
        ZoneOffset vn = ZoneOffset.ofHours(7);
        assertThat(GuildEventCoordinator.nextAt("21:00", NOW, vn)).contains(Instant.parse("2026-11-08T14:00:00Z"));
        assertThat(GuildEventCoordinator.nextAt("8:15", NOW, vn)).contains(Instant.parse("2026-11-09T01:15:00Z"));
        assertThat(GuildEventCoordinator.nextAt("24:00", NOW, vn)).isEmpty();
        assertThat(GuildEventCoordinator.nextAt("tối nay", NOW, vn)).isEmpty();
    }
}
