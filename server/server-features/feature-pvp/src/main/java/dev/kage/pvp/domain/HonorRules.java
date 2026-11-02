package dev.kage.pvp.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Luật điểm vinh dự đấu trường (ADR-155) — hằng số + ngày/tuần giờ Việt Nam. */
public final class HonorRules {

    public static final int WIN_POINTS = 10;
    public static final int LOSS_POINTS = 3;

    /** Trần điểm kiếm được mỗi ngày — chống hai người quen đánh nhau cày điểm. */
    public static final int DAILY_CAP = 100;

    private static final ZoneId VIETNAM = ZoneId.of("Asia/Ho_Chi_Minh");

    private HonorRules() {}

    /** Ngày giờ Việt Nam chứa {@code now}. */
    public static LocalDate day(Instant now) {
        return now.atZone(VIETNAM).toLocalDate();
    }

    /** Thứ Hai của tuần chứa {@code now} (giờ Việt Nam). */
    public static LocalDate weekStart(Instant now) {
        return day(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Điểm thực nhận khi đã kiếm {@code gainedToday} hôm nay và trận này cho {@code points}. */
    public static int capped(int gainedToday, int points) {
        return Math.max(0, Math.min(points, DAILY_CAP - gainedToday));
    }
}
