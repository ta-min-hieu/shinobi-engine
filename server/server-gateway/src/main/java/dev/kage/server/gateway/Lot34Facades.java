package dev.kage.server.gateway;

import dev.kage.shop.api.BuybackFacade;

/** Facade lô 34 (ADR-199…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot34Facades(BuybackFacade buyback) {

    /** Composition không có các tính năng này (test). */
    static final Lot34Facades DISABLED = new Lot34Facades(BuybackFacade.DISABLED);
}
