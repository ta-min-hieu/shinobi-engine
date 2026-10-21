package dev.kage.server.gateway;

import dev.kage.guild.domain.GuildLevelTable;
import dev.kage.guild.domain.GuildStanding;
import dev.kage.guild.domain.GuildWeeklyQuest;
import dev.kage.protocol.GuildProgressView;

/** Dịch {@link GuildStanding} sang {@code GuildProgressView} (ADR-068/069). */
final class GuildProgressViews {

    private GuildProgressViews() {}

    static GuildProgressView of(GuildStanding standing) {
        return GuildProgressView.newBuilder()
                .setLevel(standing.level())
                .setExp(standing.exp())
                .setExpForNextLevel(GuildLevelTable.expForNextLevel(standing.level()))
                .setMaxMembers(GuildLevelTable.maxMembers(standing.level()))
                .setMyContribution(standing.myContribution())
                .setWeeklyKills(Math.min(standing.weeklyKills(), GuildWeeklyQuest.TARGET_KILLS))
                .setWeeklyTarget(GuildWeeklyQuest.TARGET_KILLS)
                .setWeeklyCompleted(standing.weeklyCompleted())
                .build();
    }
}
