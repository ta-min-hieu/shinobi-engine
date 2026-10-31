package dev.kage.world.domain;

import java.time.Instant;
import java.util.random.RandomGenerator;

/**
 * Ngày đêm (ADR-123) — hàm thuần theo đồng hồ server: một giờ thật là một ngày trong game, phút
 * {@value #NIGHT_FROM_MINUTE}–59 của mỗi giờ (UTC) là đêm. Đêm: quái đánh mạnh hơn
 * ({@value #NIGHT_MONSTER_DAMAGE_PERCENT}% sát thương đòn thường), rơi đồ kỳ vọng +{@value #NIGHT_DROP_BONUS_PERCENT}%
 * (một lượt tung rơi đồ thêm với xác suất đó), vài điểm spawn chỉ ra quái ban đêm.
 */
public final class DayNightCycle {

    public static final int NIGHT_FROM_MINUTE = 40;
    public static final int NIGHT_MONSTER_DAMAGE_PERCENT = 115;
    public static final int DAY_MONSTER_DAMAGE_PERCENT = 100;
    public static final int NIGHT_DROP_BONUS_PERCENT = 20;

    private static final long SECONDS_PER_MINUTE = 60L;
    private static final long SECONDS_PER_CYCLE = 3_600L;
    private static final int PERCENT = 100;

    private DayNightCycle() {}

    /** Giây đã qua trong chu kỳ (giờ) hiện tại, 0..3599. */
    public static long secondOfCycle(Instant now) {
        return Math.floorMod(now.getEpochSecond(), SECONDS_PER_CYCLE);
    }

    public static boolean isNight(Instant now) {
        return secondOfCycle(now) >= NIGHT_FROM_MINUTE * SECONDS_PER_MINUTE;
    }

    /** Giây còn lại tới lần đổi ngày/đêm kế tiếp. */
    public static long secondsUntilChange(Instant now) {
        long second = secondOfCycle(now);
        long nightStart = NIGHT_FROM_MINUTE * SECONDS_PER_MINUTE;
        return second < nightStart ? nightStart - second : SECONDS_PER_CYCLE - second;
    }

    /** Phần trăm sát thương đòn thường của quái lúc {@code now}. */
    public static int monsterDamagePercent(Instant now) {
        return isNight(now) ? NIGHT_MONSTER_DAMAGE_PERCENT : DAY_MONSTER_DAMAGE_PERCENT;
    }

    /** Số lượt tung rơi đồ cho một lần hạ quái (trước khi nhân sự kiện x2): đêm có thêm một lượt với 20%. */
    public static int lootRolls(Instant now, RandomGenerator random) {
        return isNight(now) && random.nextInt(PERCENT) < NIGHT_DROP_BONUS_PERCENT ? 2 : 1;
    }
}
