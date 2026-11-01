package dev.kage.world.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * Sát khí (ADR-142, đề xuất cân bằng do AI chọn): hạ một người chơi trong dải PvP tự do +1 sát khí, tối đa
 * {@value #MAX_POINTS}; tự tan 1 điểm mỗi {@link #DECAY_PER_POINT} kể cả khi offline. Từ {@value #OUTLAW_POINTS} điểm
 * là <b>Ma Đầu</b>: trạm an toàn giữa dải PvP không che, không dùng được truyền tống, chết thì rơi
 * {@value #GOLD_DROP_PERCENT}% vàng (tối đa {@value #GOLD_DROP_CAP}) — về tay người hạ nếu là người chơi.
 */
public final class KillingIntentRules {

    /** Trần sát khí. */
    public static final int MAX_POINTS = 10;

    /** Ngưỡng Ma Đầu. */
    public static final int OUTLAW_POINTS = 3;

    /** Mỗi điểm tự tan sau chừng này. */
    public static final Duration DECAY_PER_POINT = Duration.ofMinutes(30);

    /** Phần trăm vàng rơi khi Ma Đầu chết. */
    public static final int GOLD_DROP_PERCENT = 10;

    /** Trần vàng rơi mỗi lần chết. */
    public static final long GOLD_DROP_CAP = 5_000L;

    private static final int PERCENT = 100;

    private KillingIntentRules() {}

    /** Sát khí còn lại sau phân rã — {@code stored} điểm ghi lúc {@code updatedAt}. */
    public static int effective(int stored, Instant updatedAt, Instant now) {
        long elapsed = Math.max(0L, Duration.between(updatedAt, now).toSeconds());
        long decayed = elapsed / DECAY_PER_POINT.toSeconds();
        return (int) Math.max(0L, stored - decayed);
    }

    /** Sát khí sau khi hạ thêm một người. */
    public static int afterKill(int effective) {
        return Math.min(MAX_POINTS, effective + 1);
    }

    public static boolean isOutlaw(int effective) {
        return effective >= OUTLAW_POINTS;
    }

    /** Vàng rơi của một Ma Đầu có {@code gold} vàng. */
    public static long goldDrop(long gold) {
        return Math.min(GOLD_DROP_CAP, Math.max(0L, gold) * GOLD_DROP_PERCENT / PERCENT);
    }
}
