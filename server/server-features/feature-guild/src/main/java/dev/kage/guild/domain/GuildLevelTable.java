package dev.kage.guild.domain;

/**
 * Cấp bang 1–5 theo kinh nghiệm bang tích luỹ (ADR-068 D1). Tham số cân bằng — đổi ở đây, không rải rác.
 */
public final class GuildLevelTable {

    public static final int MAX_LEVEL = 5;

    /** Trần sĩ số ở cấp 1 — đúng con số chủ dự án chốt 2026-10-11. */
    public static final int BASE_MAX_MEMBERS = 50;

    public static final int MEMBERS_PER_LEVEL = 10;

    /** Kinh nghiệm TÍCH LUỸ để đạt cấp {@code i + 1}. */
    private static final long[] EXP_TO_REACH = {0L, 10_000L, 30_000L, 70_000L, 150_000L};

    private GuildLevelTable() {}

    /** Cấp ứng với tổng kinh nghiệm {@code exp}. */
    public static int levelFor(long exp) {
        int level = 1;
        for (int i = 1; i < EXP_TO_REACH.length; i++) {
            if (exp >= EXP_TO_REACH[i]) {
                level = i + 1;
            }
        }
        return level;
    }

    /** Kinh nghiệm tích luỹ tối thiểu của cấp {@code level} (dưới mức này là tụt cấp) — ADR-239. */
    public static long expToReach(int level) {
        return EXP_TO_REACH[Math.clamp(level, 1, MAX_LEVEL) - 1];
    }

    /** Tổng kinh nghiệm cần để lên cấp kế; 0 nếu đã tối đa. */
    public static long expForNextLevel(int level) {
        return level >= MAX_LEVEL ? 0L : EXP_TO_REACH[Math.max(level, 1)];
    }

    /** Sĩ số tối đa ở cấp {@code level}. */
    public static int maxMembers(int level) {
        return BASE_MAX_MEMBERS + MEMBERS_PER_LEVEL * (Math.clamp(level, 1, MAX_LEVEL) - 1);
    }
}
