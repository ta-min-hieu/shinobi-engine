package dev.kage.guild.domain;

/**
 * Bang của một nhân vật nhìn từ người đó (ADR-068/069) — cho cửa sổ Bang.
 *
 * @param guildId bang
 * @param level cấp bang
 * @param exp kinh nghiệm bang tích luỹ
 * @param myContribution điểm cống hiến của chính người hỏi
 * @param weeklyKills số yêu thú cả bang đã hạ tuần này
 * @param weeklyCompleted nhiệm vụ tuần này đã xong
 */
public record GuildStanding(
        long guildId, int level, long exp, long myContribution, int weeklyKills, boolean weeklyCompleted) {}
