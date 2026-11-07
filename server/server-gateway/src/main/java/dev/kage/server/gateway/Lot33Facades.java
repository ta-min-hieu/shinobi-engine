package dev.kage.server.gateway;

import dev.kage.character.api.LastOnlineFacade;

/** Facade lô 33 (ADR-191…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot33Facades(LastOnlineFacade lastOnline) {

    /** Composition không có các tính năng này (test). */
    static final Lot33Facades DISABLED = new Lot33Facades(LastOnlineFacade.DISABLED);
}
