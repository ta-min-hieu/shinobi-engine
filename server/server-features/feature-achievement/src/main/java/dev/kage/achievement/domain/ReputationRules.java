package dev.kage.achievement.domain;

import java.util.List;
import java.util.OptionalLong;

/** Luật danh vọng (ADR-109) — thuần. Số là PLACEHOLDER cân bằng. */
public final class ReputationRules {

    /** Điểm TÍCH LUỸ tối thiểu của từng bậc 0..4 (Vô Danh → Danh Trấn Tông Môn). */
    public static final List<Long> TIER_THRESHOLDS = List.of(0L, 100L, 300L, 600L, 1000L);

    /** Lĩnh một nhiệm vụ ngày. */
    public static final int DAILY_QUEST_POINTS = 10;

    /** Xong một truy nã. */
    public static final int BOUNTY_POINTS = 5;

    /** Thành viên bang thắng bang chiến. */
    public static final int GUILD_WAR_WIN_POINTS = 20;

    private ReputationRules() {}

    /** Bậc theo điểm tích luỹ — không bao giờ tụt khi tiêu điểm. */
    public static int tierOf(long earned) {
        int tier = 0;
        for (int i = 0; i < TIER_THRESHOLDS.size(); i++) {
            if (earned >= TIER_THRESHOLDS.get(i)) {
                tier = i;
            }
        }
        return tier;
    }

    /** Điểm tích luỹ cần cho bậc kế; rỗng nếu đã bậc cao nhất. */
    public static OptionalLong nextTierAt(long earned) {
        int next = tierOf(earned) + 1;
        return next < TIER_THRESHOLDS.size() ? OptionalLong.of(TIER_THRESHOLDS.get(next)) : OptionalLong.empty();
    }
}
