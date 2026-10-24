package dev.kage.server.gateway;

import dev.kage.character.api.SeclusionFacade;
import dev.kage.checkin.api.OnlineRewardFacade;
import dev.kage.inventory.api.DismantleFacade;
import dev.kage.world.api.HerbNodeFacade;

/** Tính năng hằng ngày lô 21 phần 2 (ADR-095..098) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record DailyFacades(
        SeclusionFacade seclusion, OnlineRewardFacade onlineRewards, DismantleFacade dismantle, HerbNodeFacade herbs) {

    /** Composition không có các tính năng này (test). */
    static final DailyFacades DISABLED = new DailyFacades(
            SeclusionFacade.DISABLED, OnlineRewardFacade.DISABLED, DismantleFacade.DISABLED, HerbNodeFacade.DISABLED);
}
