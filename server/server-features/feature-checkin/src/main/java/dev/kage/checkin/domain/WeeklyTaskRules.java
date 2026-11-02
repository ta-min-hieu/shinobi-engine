package dev.kage.checkin.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Luật nhiệm vụ tuần (ADR-154) — tuần bắt đầu thứ Hai 0h giờ Việt Nam ({@link FortuneRules#VIETNAM}). */
public final class WeeklyTaskRules {

    /** Mã việc "hạ quái" — chỉ nhiệm vụ tuần đếm, lịch hoạt động ngày không có. */
    public static final String MONSTER_KILL = "monster_kill";

    private WeeklyTaskRules() {}

    /** Thứ Hai của tuần chứa {@code now}. */
    public static LocalDate weekStart(Instant now) {
        return FortuneRules.day(now).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
