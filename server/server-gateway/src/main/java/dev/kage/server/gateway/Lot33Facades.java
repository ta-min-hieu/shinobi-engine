package dev.kage.server.gateway;

import dev.kage.character.api.LastOnlineFacade;
import dev.kage.inventory.api.PetNameFacade;
import dev.kage.market.api.MarketWatchFacade;
import dev.kage.pvp.api.EnemyListFacade;

/** Facade lô 33 (ADR-191…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot33Facades(
        LastOnlineFacade lastOnline, PetNameFacade petName, EnemyListFacade enemies, MarketWatchFacade marketWatch) {

    /** Composition không có các tính năng này (test). */
    static final Lot33Facades DISABLED = new Lot33Facades(
            LastOnlineFacade.DISABLED, PetNameFacade.DISABLED, EnemyListFacade.DISABLED, MarketWatchFacade.DISABLED);
}
