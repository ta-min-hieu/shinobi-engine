package dev.kage.server.gateway;

import dev.kage.inventory.api.StackSplitFacade;
import dev.kage.mail.api.MailHousekeepingFacade;

/**
 * Facade lô 36 (ADR-215…) gom thành MỘT thành phần của {@link Lot35Facades} (lồng nhau để {@code
 * GatewayBootstrap.expansionFacades} khỏi chạm trần {@code MethodLength}).
 */
record Lot36Facades(StackSplitFacade stackSplit, MailHousekeepingFacade mailHousekeeping) {

    /** Composition không có các tính năng này (test). */
    static final Lot36Facades DISABLED = new Lot36Facades(StackSplitFacade.DISABLED, MailHousekeepingFacade.DISABLED);
}
