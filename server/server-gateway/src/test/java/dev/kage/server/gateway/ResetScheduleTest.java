package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

/** ADR-200 — mốc làm mới theo giờ Việt Nam (UTC+7). */
final class ResetScheduleTest {

    @Test
    void dailyAndWeeklyBoundaries() {
        // Thứ Bảy 2026-11-07 10:00 giờ VN = 03:00 UTC.
        Instant saturday = Instant.parse("2026-11-07T03:00:00Z");
        assertThat(ResetSchedule.nextDaily(saturday)).isEqualTo(Instant.parse("2026-11-07T17:00:00Z"));
        assertThat(ResetSchedule.nextWeekly(saturday)).isEqualTo(Instant.parse("2026-11-08T17:00:00Z"));
        // Thứ Hai 2026-11-09 00:30 giờ VN: tuần mới vừa bắt đầu ⇒ mốc tuần là thứ Hai sau.
        Instant monday = Instant.parse("2026-11-08T17:30:00Z");
        assertThat(ResetSchedule.nextWeekly(monday)).isEqualTo(Instant.parse("2026-11-15T17:00:00Z"));
        // 23:59 giờ VN ⇒ mốc ngày là một phút sau.
        assertThat(ResetSchedule.nextDaily(Instant.parse("2026-11-07T16:59:00Z")))
                .isEqualTo(Instant.parse("2026-11-07T17:00:00Z"));
    }
}
