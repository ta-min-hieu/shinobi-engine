package dev.kage.server.gateway;

import java.time.Clock;
import java.util.function.LongToIntFunction;

import dev.kage.character.domain.GoldHistoryPort;
import dev.kage.inventory.api.GemRemovalFacade;
import dev.kage.inventory.domain.GemRemovalPort;
import dev.kage.protocol.GetGoldHistoryResponse;
import dev.kage.protocol.GoldHistoryEntry;
import dev.kage.protocol.UnsocketGemCommand;
import dev.kage.protocol.UnsocketGemOutcome;
import dev.kage.protocol.UnsocketGemResponse;

/** Lớp lệnh lô 30 (ADR-167…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot30Commands(
        MailBulkCommands mailBulk,
        JunkSaleCommands junkSale,
        InspectCommands inspect,
        GuildRenameCommands guildRename,
        Lot30Facades facades,
        Clock clock) {

    static Lot30Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        return new Lot30Commands(
                new MailBulkCommands(gateway.contentFacades().socialFacades().mailFacade()),
                new JunkSaleCommands(gateway.progression().expansion().lot30().junkSale(), Clock.systemUTC()),
                new InspectCommands(new InspectCommands.Deps(
                        gateway.characterFacade(),
                        gateway.inventoryFacade(),
                        gateway.progression().expansion().lot30().inspectPrivacy())),
                new GuildRenameCommands(new GuildRenameCommands.Deps(
                        gateway.progression().expansion().lot30().guildRename(),
                        gateway.contentFacades().socialFacades().guildFacade(),
                        worldGateway.registry(),
                        Clock.systemUTC())),
                gateway.progression().expansion().lot30(),
                Clock.systemUTC());
    }

    /** ADR-169 — Virtual Thread; sổ vàng của chính {@code characterId} (từ session). */
    GetGoldHistoryResponse goldHistory(long characterId) {
        GetGoldHistoryResponse.Builder response = GetGoldHistoryResponse.newBuilder();
        for (GoldHistoryPort.Entry entry : facades.goldHistory().recent(characterId)) {
            response.addEntries(GoldHistoryEntry.newBuilder()
                    .setDelta(entry.delta())
                    .setBalance(entry.balance())
                    .setReasonKey(entry.reasonKey())
                    .setAtEpochMs(entry.at().toEpochMilli()));
        }
        return response.build();
    }

    /**
     * ADR-168 — Virtual Thread; {@code characterId} từ session. {@code reapplyStats} áp lại chỉ số trang bị (món đang
     * mặc vừa mất viên ngọc) và trả máu tối đa mới — chỉ gateway handler làm được việc đó.
     */
    UnsocketGemResponse unsocketGem(long characterId, UnsocketGemCommand command, LongToIntFunction reapplyStats) {
        GemRemovalPort.Removed removed =
                facades.gemRemoval().unsocket(characterId, command.getItemId(), clock.instant());
        UnsocketGemResponse.Builder response = UnsocketGemResponse.newBuilder()
                .setOutcome(UnsocketGemOutcome.valueOf(
                        "UNSOCKET_GEM_OUTCOME_" + removed.outcome().name()))
                .setGemItemTemplateId(removed.gemItemTemplateId());
        if (removed.outcome() == GemRemovalPort.Outcome.OK) {
            response.setFee(GemRemovalFacade.UNSOCKET_FEE).setMaxHp(reapplyStats.applyAsInt(characterId));
        }
        return response.build();
    }
}
