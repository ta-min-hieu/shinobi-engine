package dev.kage.server.gateway;

import dev.kage.inventory.api.GemRemovalFacade;

/** Facade lô 30 (ADR-167…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot30Facades(GemRemovalFacade gemRemoval) {

    /** Composition không có các tính năng này (test). */
    static final Lot30Facades DISABLED = new Lot30Facades(GemRemovalFacade.DISABLED);
}
