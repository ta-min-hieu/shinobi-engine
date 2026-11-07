package dev.kage.server.gateway;

import dev.kage.guild.api.GuildCheckInFacade;
import dev.kage.guild.api.GuildLeadershipFacade;
import dev.kage.guild.api.GuildRecruitmentFacade;

/** Facade lô 32 (ADR-183…) gom thành MỘT thành phần của {@link ExpansionFacades}. */
record Lot32Facades(
        GuildLeadershipFacade guildLeadership,
        GuildRecruitmentFacade guildRecruitment,
        GuildCheckInFacade guildCheckIn) {

    /** Composition không có các tính năng này (test). */
    static final Lot32Facades DISABLED = new Lot32Facades(
            GuildLeadershipFacade.DISABLED, GuildRecruitmentFacade.DISABLED, GuildCheckInFacade.DISABLED);
}
