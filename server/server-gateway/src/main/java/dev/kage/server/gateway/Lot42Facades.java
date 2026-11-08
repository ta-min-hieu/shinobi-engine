package dev.kage.server.gateway;

import dev.kage.party.api.PartyGoldSplitFacade;

/** Facade lô 42–43 (ADR-263…) gom thành MỘT thành phần của {@link Lot41Facades}. */
record Lot42Facades(PartyGoldSplitFacade partyGoldSplit) {

    /** Composition không có các tính năng này (test). */
    static final Lot42Facades DISABLED = new Lot42Facades(PartyGoldSplitFacade.DISABLED);
}
