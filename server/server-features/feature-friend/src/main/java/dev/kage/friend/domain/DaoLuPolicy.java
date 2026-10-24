package dev.kage.friend.domain;

/** Đạo lữ (ADR-094) — tham số cân bằng ở một chỗ, hàm thuần. Số là PLACEHOLDER cân bằng. */
public final class DaoLuPolicy {

    /** Thân mật tối thiểu của cặp bạn để kết đạo lữ — mốc thân mật cao nhất của ADR-084. */
    public static final int MIN_INTIMACY = 600;

    /** Linh thạch người cầu bỏ ra lúc được nhận lời (đốt — gold sink). */
    public static final long BIND_COST = 5_000L;

    /** EXP đánh quái cộng thêm (phần trăm) khi đạo lữ cùng nhóm. */
    public static final long EXP_BONUS_PERCENT = 10L;

    private static final long PERCENT = 100L;

    private DaoLuPolicy() {}

    /** EXP sau khi cộng thưởng đạo lữ cùng nhóm. */
    public static long boostedExp(long amount) {
        return amount + amount * EXP_BONUS_PERCENT / PERCENT;
    }
}
