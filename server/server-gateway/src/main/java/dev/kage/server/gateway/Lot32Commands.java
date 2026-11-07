package dev.kage.server.gateway;

import java.time.Clock;

/** Lớp lệnh lô 32 (ADR-183…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot32Commands(
        GuildLeadershipCommands guildLeadership, GuildRecruitmentCommands guildRecruitment, Lot32Facades facades) {

    static Lot32Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        Lot32Facades lot32 = gateway.progression().expansion().lot32();
        return new Lot32Commands(
                new GuildLeadershipCommands(new GuildLeadershipCommands.Deps(
                        lot32.guildLeadership(),
                        gateway.contentFacades().socialFacades().guildFacade(),
                        gateway.characterFacade(),
                        worldGateway.registry(),
                        Clock.systemUTC())),
                new GuildRecruitmentCommands(lot32.guildRecruitment(), Clock.systemUTC()),
                lot32);
    }
}
