package dev.kage.server.gateway;

import dev.kage.achievement.api.TitleBonusFacade;
import dev.kage.guild.api.GuildItemDonationFacade;
import dev.kage.guild.api.GuildWarHistoryFacade;
import dev.kage.mail.api.StrangerMailFacade;

/**
 * Facade lô 35 (ADR-207…) gom thành MỘT thành phần của {@link Lot34Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot35Facades(
        GuildItemDonationFacade guildItemDonation,
        GuildWarHistoryFacade warHistory,
        TitleBonusFacade titleBonus,
        StrangerMailFacade strangerMail) {

    /** Composition không có các tính năng này (test). */
    static final Lot35Facades DISABLED = new Lot35Facades(
            GuildItemDonationFacade.DISABLED,
            GuildWarHistoryFacade.DISABLED,
            TitleBonusFacade.DISABLED,
            StrangerMailFacade.DISABLED);
}
