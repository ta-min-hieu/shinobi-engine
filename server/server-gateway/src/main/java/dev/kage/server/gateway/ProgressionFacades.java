package dev.kage.server.gateway;

import dev.kage.achievement.api.AchievementFacade;
import dev.kage.checkin.api.CheckInFacade;
import dev.kage.crafting.api.CraftingFacade;
import dev.kage.gm.api.GameEventFacade;
import dev.kage.guild.api.GuildProgressionFacade;
import dev.kage.guild.api.GuildShopFacade;
import dev.kage.market.api.MarketFacade;
import dev.kage.monster.api.WorldBossFacade;

/**
 * Facade của các tính năng gắn bó lô 16–17 (2026-10-18, 2026-10-21) — gom để {@link GatewayFacades} không phình thêm
 * một thành phần mỗi tính năng.
 */
record ProgressionFacades(
        CheckInFacade checkInFacade,
        AchievementFacade achievementFacade,
        MarketFacade marketFacade,
        GameEventFacade gameEvents,
        GuildProgressionFacade guildProgression,
        CraftingFacade crafting,
        GuildShopFacade guildShop,
        WorldBossFacade worldBoss) {

    /** Composition không có các tính năng này (test). */
    static final ProgressionFacades DISABLED = new ProgressionFacades(
            CheckInFacade.DISABLED,
            AchievementFacade.DISABLED,
            MarketFacade.DISABLED,
            GameEventFacade.DISABLED,
            GuildProgressionFacade.DISABLED,
            CraftingFacade.DISABLED,
            GuildShopFacade.DISABLED,
            WorldBossFacade.DISABLED);
}
