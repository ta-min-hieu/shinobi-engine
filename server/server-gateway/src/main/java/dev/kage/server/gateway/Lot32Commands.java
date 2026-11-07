package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.guild.domain.GuildCheckInPort;
import dev.kage.protocol.GetTradeHistoryResponse;
import dev.kage.protocol.GuildCheckInOutcome;
import dev.kage.protocol.GuildCheckInResponse;
import dev.kage.protocol.TradeHistoryEntry;
import dev.kage.protocol.TradeHistoryItem;
import dev.kage.trade.domain.TradeHistoryPort;

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

    /** ADR-189 — Virtual Thread; lịch sử giao dịch của chính {@code characterId} (từ session). */
    GetTradeHistoryResponse tradeHistory(long characterId) {
        GetTradeHistoryResponse.Builder response = GetTradeHistoryResponse.newBuilder();
        for (TradeHistoryPort.Entry entry : facades.tradeHistory().recent(characterId, java.time.Instant.now())) {
            TradeHistoryEntry.Builder view = TradeHistoryEntry.newBuilder()
                    .setCounterpartName(entry.counterpartName())
                    .setGoldGiven(entry.goldGiven())
                    .setGoldReceived(entry.goldReceived())
                    .setAtEpochSeconds(entry.at().getEpochSecond());
            entry.given().forEach(item -> view.addGiven(wire(item)));
            entry.received().forEach(item -> view.addReceived(wire(item)));
            response.addEntries(view);
        }
        return response.build();
    }

    private static TradeHistoryItem wire(TradeHistoryPort.Item item) {
        return TradeHistoryItem.newBuilder()
                .setItemTemplateId(item.itemTemplateId())
                .setQuantity(item.quantity())
                .setEnhanceLevel(item.enhanceLevel())
                .build();
    }
}
