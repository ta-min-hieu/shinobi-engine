package dev.kage.server.gateway;

import dev.kage.inventory.api.ItemLookupFacade;
import dev.kage.party.api.PartyAutoAcceptFacade;
import dev.kage.pvp.api.DuelHistoryFacade;

/** Facade lô 44 (ADR-279…) gom thành MỘT thành phần của {@link Lot42Facades}. */
record Lot44Facades(ItemLookupFacade itemLookup, DuelHistoryFacade duelHistory, PartyAutoAcceptFacade partyAutoAccept) {

    /** Composition không có các tính năng này (test). */
    static final Lot44Facades DISABLED =
            new Lot44Facades(ItemLookupFacade.DISABLED, DuelHistoryFacade.DISABLED, PartyAutoAcceptFacade.DISABLED);
}
