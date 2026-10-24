package dev.kage.guild.domain;

/**
 * Điểm cống hiến tiêu được của một thành viên (ADR-080 D1).
 *
 * @param guildLevel cấp bang hiện tại
 * @param available cống hiến tích luỹ trừ đã tiêu
 */
public record GuildShopWallet(int guildLevel, long available) {}
