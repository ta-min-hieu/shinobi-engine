package dev.kage.server.gateway;

import dev.kage.account.api.LoginHistoryFacade;
import dev.kage.friend.api.ContactPrivacyFacade;
import dev.kage.guild.api.GuildMailFacade;
import dev.kage.inventory.api.ItemDestroyFacade;
import dev.kage.inventory.api.ItemHistoryFacade;
import dev.kage.market.api.MarketPriceHintFacade;

/**
 * Facade lô 38 (ADR-231…) gom thành MỘT thành phần của {@link Lot37Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot38Facades(
        ItemDestroyFacade itemDestroy,
        ItemHistoryFacade itemHistory,
        MarketPriceHintFacade priceHint,
        GuildMailFacade guildMail,
        ContactPrivacyFacade contactPrivacy,
        LoginHistoryFacade loginHistory,
        Lot39Facades lot39) {

    /** Composition không có các tính năng này (test). */
    static final Lot38Facades DISABLED = new Lot38Facades(
            ItemDestroyFacade.DISABLED,
            ItemHistoryFacade.DISABLED,
            MarketPriceHintFacade.DISABLED,
            GuildMailFacade.DISABLED,
            ContactPrivacyFacade.DISABLED,
            LoginHistoryFacade.DISABLED,
            Lot39Facades.DISABLED);
}
