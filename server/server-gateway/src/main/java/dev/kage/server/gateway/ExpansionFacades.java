package dev.kage.server.gateway;

import dev.kage.account.api.SecurityPinFacade;
import dev.kage.market.api.BuyOrderFacade;
import dev.kage.shop.api.TravelingMerchantFacade;
import dev.kage.world.api.ConsumableFacade;
import dev.kage.world.api.SpiritFieldFacade;

/** Lô 23 (ADR-115…126) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record ExpansionFacades(
        SecurityPinFacade securityPin,
        BuyOrderFacade buyOrders,
        ConsumableFacade consumables,
        TravelingMerchantFacade merchant,
        SpiritFieldFacade spiritField) {

    /** Composition không có các tính năng này (test). */
    static final ExpansionFacades DISABLED = new ExpansionFacades(
            SecurityPinFacade.DISABLED,
            BuyOrderFacade.DISABLED,
            ConsumableFacade.DISABLED,
            TravelingMerchantFacade.DISABLED,
            SpiritFieldFacade.DISABLED);
}
