package dev.kage.quest.domain;

/** Quest làm được mấy lần (ADR-055, cột {@code quest_template.repeat_policy}). */
public enum RepeatPolicy {
    /** Một lần trong đời nhân vật — mọi quest trước ADR-055. */
    ONCE,
    /** Làm lại được sau mỗi mốc 0h giờ Việt Nam, kể từ lần lĩnh thưởng gần nhất ({@link DailyReset}). */
    DAILY
}
