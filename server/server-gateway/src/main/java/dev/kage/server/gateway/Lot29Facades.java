package dev.kage.server.gateway;

import dev.kage.inventory.api.BagSortFacade;
import dev.kage.inventory.api.ItemLockFacade;

/** Facade lô 29 (ADR-159…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot29Facades(ItemLockFacade itemLock, BagSortFacade bagSort) {

    /** Composition không có các tính năng này (test). */
    static final Lot29Facades DISABLED = new Lot29Facades(ItemLockFacade.DISABLED, BagSortFacade.DISABLED);
}
