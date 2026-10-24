package dev.kage.guild.domain;

/** Kết quả đổi một món ở cửa hàng bang (ADR-080). */
public enum GuildShopOutcome {
    OK,
    NOT_IN_GUILD,
    /** Món không bán ở cửa hàng bang. */
    UNKNOWN_ITEM,
    GUILD_LEVEL_TOO_LOW,
    NOT_ENOUGH_CONTRIBUTION,
    BAG_FULL
}
