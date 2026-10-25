package dev.kage.friend.domain;

/** Kết nghĩa huynh đệ (ADR-110) — tham số cân bằng ở một chỗ, hàm thuần. Số là PLACEHOLDER cân bằng. */
public final class SwornPolicy {

    /** Thân mật tối thiểu giữa người mời và người được mời. */
    public static final int MIN_INTIMACY = 300;

    /** Sĩ số tối đa một nhóm kết nghĩa. */
    public static final int MAX_MEMBERS = 5;

    /** Linh thạch người mời trả khi LẬP nhóm mới (đốt — gold sink). */
    public static final long FOUND_COST = 3_000L;

    /** Linh thạch người mời trả khi thêm một người vào nhóm đã có. */
    public static final long JOIN_COST = 1_000L;

    /** EXP đánh quái cộng thêm (phần trăm) khi có ít nhất một huynh đệ cùng nhóm. */
    public static final long EXP_BONUS_PERCENT = 5L;

    private static final long PERCENT = 100L;

    private SwornPolicy() {}

    /** EXP sau thưởng kết nghĩa. */
    public static long boostedExp(long amount) {
        return amount + amount * EXP_BONUS_PERCENT / PERCENT;
    }
}
