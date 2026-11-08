package dev.kage.server.gateway;

import dev.kage.inventory.api.ItemDestroyFacade;
import dev.kage.inventory.api.ItemHistoryFacade;
import dev.kage.market.api.MarketPriceHintFacade;

/**
 * Facade lô 38 (ADR-231…) gom thành MỘT thành phần của {@link Lot37Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot38Facades(ItemDestroyFacade itemDestroy, ItemHistoryFacade itemHistory, MarketPriceHintFacade priceHint) {

    /** Composition không có các tính năng này (test). */
    static final Lot38Facades DISABLED =
            new Lot38Facades(ItemDestroyFacade.DISABLED, ItemHistoryFacade.DISABLED, MarketPriceHintFacade.DISABLED);
}
