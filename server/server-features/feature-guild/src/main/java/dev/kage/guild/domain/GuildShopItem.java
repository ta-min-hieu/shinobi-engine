package dev.kage.guild.domain;

/**
 * Một món ở cửa hàng bang (ADR-080 D2) — dữ liệu bảng {@code guild_shop_item}.
 *
 * @param itemTemplateId món
 * @param contributionCost giá bằng điểm cống hiến
 * @param requiredGuildLevel cấp bang tối thiểu để đổi
 */
public record GuildShopItem(long itemTemplateId, long contributionCost, int requiredGuildLevel) {}
