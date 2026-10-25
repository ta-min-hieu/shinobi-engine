package dev.kage.server.gateway;

import dev.kage.guild.api.GuildWarFacade;
import dev.kage.quest.api.BountyFacade;
import dev.kage.world.api.DungeonQuotaFacade;
import dev.kage.world.api.WaypointFacade;

/** Nội dung lô 21 phần 3–4 (ADR-099..102) — gom để {@link ProgressionFacades} chỉ thêm một thành phần. */
record AdventureFacades(
        DungeonQuotaFacade dungeonQuota, WaypointFacade waypoints, BountyFacade bounties, GuildWarFacade guildWars) {

    /** Composition không có các tính năng này (test) — Bí Cảnh không giới hạn lượt. */
    static final AdventureFacades DISABLED = new AdventureFacades(
            DungeonQuotaFacade.UNLIMITED, WaypointFacade.DISABLED, BountyFacade.DISABLED, GuildWarFacade.DISABLED);
}
