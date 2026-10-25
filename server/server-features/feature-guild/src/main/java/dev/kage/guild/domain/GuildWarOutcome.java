package dev.kage.guild.domain;

/** Kết quả tuyên chiến (ADR-102). */
public enum GuildWarOutcome {
    OK,
    NOT_IN_GUILD,
    NOT_LEADER,
    UNKNOWN_GUILD,
    SAME_GUILD,
    /** Một trong hai bang đang có trận chờ hoặc đang đánh. */
    ALREADY_AT_WAR,
    /** Cặp bang vừa đánh nhau chưa đủ {@link GuildWarRules#REMATCH_COOLDOWN}. */
    COOLDOWN,
    INSUFFICIENT_GOLD
}
