package dev.kage.guild.domain;

/** Kết quả nâng kỹ năng bang (ADR-085). */
public enum GuildSkillOutcome {
    OK,
    NOT_IN_GUILD,
    NOT_LEADER,
    MAX_RANK,
    NO_POINTS
}
