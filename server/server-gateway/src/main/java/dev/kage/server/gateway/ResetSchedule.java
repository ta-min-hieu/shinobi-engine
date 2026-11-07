package dev.kage.server.gateway;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/**
 * Mốc làm mới (ADR-200) — mọi thứ "hằng ngày" của server (nhiệm vụ ngày, điểm danh, quẻ bói, lượt hầm ngục, …) đổi ngày
 * lúc 00:00 giờ Việt Nam ({@code DailyReset.ZONE}); "hằng tuần" (nhiệm vụ tuần, cửa hàng vinh dự) bắt đầu thứ Hai.
 */
final class ResetSchedule {

    private static final ZoneId ZONE = dev.kage.quest.domain.DailyReset.ZONE;

    private ResetSchedule() {}

    /** 00:00 ngày mai (giờ Việt Nam) tính từ {@code now}. */
    static Instant nextDaily(Instant now) {
        return LocalDate.ofInstant(now, ZONE).plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** 00:00 thứ Hai kế tiếp (giờ Việt Nam) — đang thứ Hai thì là thứ Hai tuần sau. */
    static Instant nextWeekly(Instant now) {
        return LocalDate.ofInstant(now, ZONE)
                .with(TemporalAdjusters.next(DayOfWeek.MONDAY))
                .atStartOfDay(ZONE)
                .toInstant();
    }
}
