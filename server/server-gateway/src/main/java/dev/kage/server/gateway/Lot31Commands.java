package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.market.domain.MarketHistoryPort;
import dev.kage.market.domain.MarketPolicy;
import dev.kage.protocol.GetMarketHistoryResponse;
import dev.kage.protocol.MarketHistoryEntry;

/** Lớp lệnh lô 31 (ADR-175…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot31Commands(
        EquipmentPresetCommands equipmentPresets,
        BulkUseCommands bulkUse,
        AwayCommands away,
        Lot31Facades facades,
        Clock clock) {

    static Lot31Commands of(GatewayFacades gateway, WorldGateway worldGateway) {
        Lot31Facades lot31 = gateway.progression().expansion().lot31();
        return new Lot31Commands(
                new EquipmentPresetCommands(new EquipmentPresetCommands.Deps(
                        lot31.equipmentPresets(), gateway.inventoryFacade(), gateway.characterFacade())),
                new BulkUseCommands(
                        gateway.inventoryFacade(),
                        gateway.progression().expansion().lot29().luckyBag()),
                worldGateway.registry().away, // ADR-178 — chung mọi kết nối, KHÔNG dựng mới mỗi kết nối
                lot31,
                Clock.systemUTC());
    }

    /** ADR-177 — Virtual Thread; lịch sử phường thị của chính {@code characterId} (từ session). */
    GetMarketHistoryResponse marketHistory(long characterId) {
        GetMarketHistoryResponse.Builder response = GetMarketHistoryResponse.newBuilder();
        for (MarketHistoryPort.Entry entry : facades.marketHistory().recent(characterId, clock.instant())) {
            response.addEntries(MarketHistoryEntry.newBuilder()
                    .setSold(entry.sold())
                    .setItemTemplateId(entry.itemTemplateId())
                    .setQuantity(entry.quantity())
                    .setPrice(entry.price())
                    .setGoldDelta(entry.sold() ? MarketPolicy.proceedsOf(entry.price()) : -entry.price())
                    .setCounterpartName(entry.counterpartName())
                    .setAtEpochSeconds(entry.closedAt().getEpochSecond()));
        }
        return response.build();
    }
}
