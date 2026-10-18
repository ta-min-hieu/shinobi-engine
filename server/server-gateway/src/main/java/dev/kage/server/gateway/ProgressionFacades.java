package dev.kage.server.gateway;

import dev.kage.achievement.api.AchievementFacade;
import dev.kage.checkin.api.CheckInFacade;
import dev.kage.market.api.MarketFacade;

/**
 * Facade của các tính năng gắn bó lô 16 (2026-10-18) — gom để {@link GatewayFacades} không phình thêm
 * một thành phần mỗi tính năng.
 */
record ProgressionFacades(CheckInFacade checkInFacade, AchievementFacade achievementFacade, MarketFacade marketFacade) {

    /** Composition không có các tính năng này (test). */
    static final ProgressionFacades DISABLED =
            new ProgressionFacades(CheckInFacade.DISABLED, AchievementFacade.DISABLED, MarketFacade.DISABLED);
}
