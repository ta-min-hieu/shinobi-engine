package dev.kage.checkin.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** ADR-154 — tuần bắt đầu thứ Hai 0h giờ Việt Nam. */
class WeeklyTaskRulesTest {

    @Test
    void weekShouldStartOnMondayVietnamTime() {
        // Thứ Hai 2026-11-02 10:00 VN.
        assertThat(WeeklyTaskRules.weekStart(Instant.parse("2026-11-02T03:00:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 2));
        // Chủ nhật 2026-11-08 23:30 VN vẫn thuộc tuần đó.
        assertThat(WeeklyTaskRules.weekStart(Instant.parse("2026-11-08T16:30:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 2));
        // 00:30 thứ Hai 2026-11-09 VN (= 17:30 UTC Chủ nhật) đã là tuần mới.
        assertThat(WeeklyTaskRules.weekStart(Instant.parse("2026-11-08T17:30:00Z")))
                .isEqualTo(LocalDate.of(2026, 11, 9));
    }
}
