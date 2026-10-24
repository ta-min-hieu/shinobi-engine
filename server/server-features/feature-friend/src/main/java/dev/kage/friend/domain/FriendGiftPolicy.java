package dev.kage.friend.domain;

import java.util.List;
import java.util.Optional;

/** Tặng quà bạn bè (ADR-084) — tham số cân bằng ở một chỗ, hàm thuần. */
public final class FriendGiftPolicy {

    /** Linh thạch người tặng bỏ ra mỗi món quà (đốt — gold sink). */
    public static final long GIFT_COST = 100L;

    /** Thân mật cộng thêm cho CẶP bạn mỗi món quà (một con số chung cho cả hai chiều). */
    public static final int INTIMACY_PER_GIFT = 10;

    /** Mốc thân mật có thư thưởng cho cả hai người, tăng dần. */
    public static final List<Integer> MILESTONES = List.of(100, 300, 600);

    private FriendGiftPolicy() {}

    /** Mốc vừa vượt khi thân mật đi từ {@code before} lên {@code after}; rỗng nếu không chạm mốc nào. */
    public static Optional<Integer> milestoneCrossed(int before, int after) {
        return MILESTONES.stream()
                .filter(milestone -> before < milestone && after >= milestone)
                .findFirst();
    }
}
