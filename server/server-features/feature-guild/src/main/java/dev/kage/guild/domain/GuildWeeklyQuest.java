package dev.kage.guild.domain;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;

/** Nhiệm vụ bang tuần (ADR-069) — tham số + cách tính tuần. */
public final class GuildWeeklyQuest {

    /** Tổng số yêu thú cả bang phải hạ trong một tuần. */
    public static final int TARGET_KILLS = 300;

    /** Kinh nghiệm bang khi hoàn thành. */
    public static final long REWARD_GUILD_EXP = 5_000L;

    /** Linh thạch gửi qua thư cho MỖI thành viên lúc hoàn thành. */
    public static final long REWARD_GOLD_PER_MEMBER = 2_000L;

    /** Mốc ngày của server — cùng mốc điểm danh (ADR-060). */
    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private GuildWeeklyQuest() {}

    /** Thứ Hai của tuần chứa {@code now} (giờ Việt Nam). */
    public static LocalDate weekStart(Instant now) {
        return LocalDate.ofInstant(now, ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
