package dev.kage.server.gateway;

import java.util.List;

import dev.kage.guild.api.GuildWarFacade;
import dev.kage.quest.api.BountyFacade;
import dev.kage.quest.api.EscortRouteFacade;
import dev.kage.world.api.DungeonQuotaFacade;
import dev.kage.world.api.TowerFacade;
import dev.kage.world.api.WaypointFacade;
import dev.kage.world.api.WorldGeometryFacade;

/**
 * Nội dung lô 21 phần 3–4 (ADR-099..102) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. {@code geometry}
 * (ADR-141) trả bản đồ vùng PvP/an toàn cho HUD.
 */
record AdventureFacades(
        DungeonQuotaFacade dungeonQuota,
        WaypointFacade waypoints,
        BountyFacade bounties,
        GuildWarFacade guildWars,
        TowerFacade tower,
        EscortRouteFacade escortRoutes,
        WorldGeometryFacade geometry) {

    /** Composition không có các tính năng này (test) — Bí Cảnh không giới hạn lượt. */
    static final AdventureFacades DISABLED = new AdventureFacades(
            DungeonQuotaFacade.UNLIMITED,
            WaypointFacade.DISABLED,
            BountyFacade.DISABLED,
            GuildWarFacade.DISABLED,
            TowerFacade.DISABLED,
            EscortRouteFacade.DISABLED,
            new WorldGeometryFacade(worldCode -> List.of()));
}
