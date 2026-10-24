package dev.kage.server.gateway;

import dev.kage.quest.api.BountyFacade;
import dev.kage.world.api.DungeonQuotaFacade;
import dev.kage.world.api.WaypointFacade;

/** Nội dung lô 21 phần 3 (ADR-099..101) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record AdventureFacades(DungeonQuotaFacade dungeonQuota, WaypointFacade waypoints, BountyFacade bounties) {

    /** Composition không có các tính năng này (test) — Bí Cảnh không giới hạn lượt. */
    static final AdventureFacades DISABLED =
            new AdventureFacades(DungeonQuotaFacade.UNLIMITED, WaypointFacade.DISABLED, BountyFacade.DISABLED);
}
