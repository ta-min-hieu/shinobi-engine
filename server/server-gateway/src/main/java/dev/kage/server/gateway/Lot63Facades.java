package dev.kage.server.gateway;

import dev.kage.checkin.api.OnlineStatsFacade;
import dev.kage.friend.api.IntimacyRankingFacade;

/** Facade lô 63 (ADR-431…) gom thành MỘT thành phần của {@link Lot62Facades}. Báo bang đột phá dùng facade bang. */
record Lot63Facades(OnlineStatsFacade onlineStats, IntimacyRankingFacade intimacy, Lot64Facades lot64) {

    /** Composition không có các tính năng này (test). */
    static final Lot63Facades DISABLED =
            new Lot63Facades(OnlineStatsFacade.DISABLED, IntimacyRankingFacade.DISABLED, Lot64Facades.DISABLED);
}
