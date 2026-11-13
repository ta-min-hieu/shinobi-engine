package dev.kage.server.gateway;

import dev.kage.character.api.InspectNoticeFacade;
import dev.kage.character.api.LoginStreakFacade;
import dev.kage.party.api.RecentPlayerFacade;

/** Facade lô 50 (ADR-327…) gom thành MỘT thành phần của {@link Lot49Facades}. */
record Lot50Facades(
        LoginStreakFacade loginStreak, RecentPlayerFacade recentPlayers, InspectNoticeFacade inspectNotice) {

    /** Composition không có các tính năng này (test). */
    static final Lot50Facades DISABLED =
            new Lot50Facades(LoginStreakFacade.DISABLED, RecentPlayerFacade.DISABLED, InspectNoticeFacade.DISABLED);
}
