package dev.kage.character.domain;

import java.time.Duration;
import java.time.Instant;

/** Bế quan (ADR-095) — tham số cân bằng ở một chỗ, hàm thuần. Số là PLACEHOLDER cân bằng. */
public final class SeclusionPolicy {

    /** Quỹ tối đa: 8 giờ. */
    public static final int MAX_BANKED_MINUTES = 480;

    /** Đủ quỹ tối đa thì được ngần này phần một cấp (1 = trọn một cấp ở cấp lúc nhận). */
    private static final int MINUTES_PER_LEVEL = 480;

    /** Linh thạch mỗi cấp để nhận x2. */
    public static final long DOUBLE_COST_PER_LEVEL = 50L;

    private SeclusionPolicy() {}

    /** Phút offline trọn giữa hai mốc, kẹp vào {@code 0..MAX}; mốc ngược (đồng hồ lệch) cho 0. */
    public static int offlineMinutes(Instant loggedOutAt, Instant now) {
        long minutes = Duration.between(loggedOutAt, now).toMinutes();
        return (int) Math.max(0L, Math.min(MAX_BANKED_MINUTES, minutes));
    }

    /** Quỹ sau khi cộng thêm, không vượt trần. */
    public static int addToBank(int banked, int minutes) {
        return Math.min(MAX_BANKED_MINUTES, banked + minutes);
    }

    /** EXP x1 cho {@code minutes} phút ở {@code level} — 8 giờ ≈ một cấp; cấp tối đa vẫn được 1 EXP/phút. */
    public static long expFor(int minutes, int level) {
        long perMinute = Math.max(1L, ExperienceCurve.expForNextLevel(level) / MINUTES_PER_LEVEL);
        return minutes * perMinute;
    }

    /** Linh thạch để nhận x2. */
    public static long doubleCost(int level) {
        return level * DOUBLE_COST_PER_LEVEL;
    }
}
