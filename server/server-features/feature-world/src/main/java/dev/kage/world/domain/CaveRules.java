package dev.kage.world.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Động phủ (ADR-129) — luật thuần của tiểu linh mạch: sản lượng linh thạch theo giờ thật, kho chứa có trần, giá nâng
 * cấp. Mọi con số là ĐỀ XUẤT CÂN BẰNG (ADR-129 D2).
 */
public final class CaveRules {

    /** Linh thạch mỗi giờ cho MỖI cấp linh mạch. */
    public static final long STONES_PER_HOUR_PER_LEVEL = 30L;
    /** Cấp linh mạch cao nhất. */
    public static final int MAX_VEIN_LEVEL = 5;
    /** Kho chứa = chừng này giờ sản lượng; đầy thì ngừng tích. */
    public static final long STORAGE_HOURS = 24L;
    /** Giá nâng cấp = hằng số này × cấp hiện tại. */
    public static final long UPGRADE_COST_PER_LEVEL = 2_000L;
    /** Lời mời thăm động phủ còn hiệu lực trong chừng này. */
    public static final Duration INVITE_TTL = Duration.ofMinutes(10);

    private static final long PERCENT = 100L;
    private static final long SECONDS_PER_HOUR = 3_600L;

    private CaveRules() {}

    /** Sản lượng mỗi giờ ở cấp {@code level} với tổng thưởng bày trí {@code bonusPercent}. */
    public static long perHour(int level, int bonusPercent) {
        return STONES_PER_HOUR_PER_LEVEL * level * (PERCENT + bonusPercent) / PERCENT;
    }

    /** Sức chứa của kho ở cấp + thưởng này. */
    public static long capacity(int level, int bonusPercent) {
        return perHour(level, bonusPercent) * STORAGE_HOURS;
    }

    /** Linh thạch đã tích từ {@code since} tới {@code now} (làm tròn xuống, không vượt sức chứa, không âm). */
    public static long accrued(int level, int bonusPercent, Instant since, Instant now) {
        long seconds = Math.max(0L, Duration.between(since, now).toSeconds());
        long produced = perHour(level, bonusPercent) * seconds / SECONDS_PER_HOUR;
        return Math.min(produced, capacity(level, bonusPercent));
    }

    /** Giá nâng từ {@code level} lên {@code level + 1}. */
    public static long upgradeCost(int level) {
        return UPGRADE_COST_PER_LEVEL * level;
    }
}
