package dev.kage.character.domain;

import java.util.Collection;
import java.util.Map;

/** Điểm công pháp + bậc chiêu (ADR-071) + bậc tâm pháp (ADR-077) — hàm thuần. */
public final class SkillRankPolicy {

    public static final int MAX_RANK = 5;

    /** Mỗi bậc trên 1 tăng con số chiêu thức chừng này phần trăm. */
    public static final int PERCENT_PER_RANK = 10;

    private SkillRankPolicy() {}

    /** Tổng điểm ở cấp {@code level} — một điểm mỗi hai cấp (một tầng cảnh giới, ADR-038). */
    public static int totalPoints(int level) {
        return Math.max(0, level) / 2;
    }

    /** Điểm đã tiêu trên các chiêu {@code skillCodes} (chiêu không có dòng = bậc 1, chưa tiêu gì). */
    public static int spentPoints(Map<String, Integer> ranks, Collection<String> skillCodes) {
        int spent = 0;
        for (String code : skillCodes) {
            spent += Math.max(0, ranks.getOrDefault(code, 1) - 1);
        }
        return spent;
    }

    /** Tâm pháp bị động (ADR-077): mỗi bậc 1..5 tốn một điểm — vắng dòng = bậc 0, chưa học. */
    public static int spentPassivePoints(Map<String, Integer> ranks, Collection<String> passiveCodes) {
        int spent = 0;
        for (String code : passiveCodes) {
            spent += Math.clamp(ranks.getOrDefault(code, 0), 0, MAX_RANK);
        }
        return spent;
    }

    /** Bậc khi chưa có dòng nào: chiêu chủ động 1, tâm pháp 0 (ADR-077). */
    public static int baseRank(boolean passive) {
        return passive ? 0 : 1;
    }

    /** Hệ số con số chiêu theo bậc, tính bằng phần trăm: bậc 1 = 100, bậc 5 = 140. */
    public static int powerPercent(int rank) {
        return 100 + PERCENT_PER_RANK * (Math.clamp(rank, 1, MAX_RANK) - 1);
    }
}
