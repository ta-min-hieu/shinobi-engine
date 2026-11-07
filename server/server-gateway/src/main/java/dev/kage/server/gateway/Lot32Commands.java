package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.guild.domain.GuildCheckInPort;
import dev.kage.protocol.GuildCheckInOutcome;
import dev.kage.protocol.GuildCheckInResponse;

/** Lớp lệnh lô 32 (ADR-183…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot32Commands(
        GuildLeadershipCommands guildLeadership,
        GuildRecruitmentCommands guildRecruitment,
        OfflineWhisperCommands offlineWhispers,
        ItemLinkCommands itemLinks,
        Lot32Facades facades,
        ConnectedCharacterRegistry registry) {

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
                new OfflineWhisperCommands(new OfflineWhisperCommands.Deps(
                        lot32.offlineWhispers(),
                        gateway.progression().relationships().blocks(),
                        worldGateway.registry(),
                        Clock.systemUTC())),
                new ItemLinkCommands(gateway.inventoryFacade()),
                lot32,
                worldGateway.registry());
    }

    /** ADR-186 — Virtual Thread; điểm danh bang của {@code characterId} (từ session). Bang lên cấp ⇒ báo cả bang. */
    GuildCheckInResponse guildCheckIn(long characterId) {
        GuildCheckInPort.CheckIn checkIn = facades.guildCheckIn().checkIn(characterId);
        GuildCheckInResponse.Builder response = GuildCheckInResponse.newBuilder()
                .setOutcome(GuildCheckInOutcome.valueOf(
                        "GUILD_CHECK_IN_OUTCOME_" + checkIn.outcome().name()))
                .setTodayCount(checkIn.todayCount());
        if (checkIn.outcome() == GuildCheckInPort.Outcome.OK) {
            response.setContribution(GuildCheckInPort.CONTRIBUTION).setGuildExp(GuildCheckInPort.GUILD_EXP);
        }
        if (checkIn.leveledUp()) {
            registry.guildProgress.announceLevelUp(checkIn.guildId());
        }
        return response.build();
    }
}
