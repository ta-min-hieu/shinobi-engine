package dev.kage.server.gateway;

import dev.kage.mail.api.SentMailFacade;
import dev.kage.shop.api.PurchaseHistoryFacade;

/** Facade lô 45 (ADR-287…) gom thành MỘT thành phần của {@link Lot44Facades}. */
record Lot45Facades(PurchaseHistoryFacade purchaseHistory, SentMailFacade sentMail, Lot46Facades lot46) {

    /** Composition không có các tính năng này (test). */
    static final Lot45Facades DISABLED =
            new Lot45Facades(PurchaseHistoryFacade.DISABLED, SentMailFacade.DISABLED, Lot46Facades.DISABLED);
}
