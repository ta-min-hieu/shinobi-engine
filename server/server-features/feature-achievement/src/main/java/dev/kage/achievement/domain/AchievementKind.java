package dev.kage.achievement.domain;

/** Loại mốc (ADR-062 D1) — khớp CHECK của cột {@code achievement.kind}. */
public enum AchievementKind {
    KILL_MONSTERS,
    CLAIM_QUESTS,
    WIN_DUELS,
    CHECK_IN,
    /** So thẳng với cấp nhân vật — không có bộ đếm. */
    REACH_LEVEL;

    /** Có bộ đếm cộng dồn không ({@link #REACH_LEVEL} thì không). */
    public boolean isCounted() {
        return this != REACH_LEVEL;
    }
}
