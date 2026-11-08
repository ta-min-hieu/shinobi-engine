package dev.kage.server.gateway;

import java.time.Instant;

import dev.kage.inventory.domain.ItemHistoryPort;
import dev.kage.market.domain.MarketPriceHintPort;
import dev.kage.protocol.BulkDismantleCommand;
import dev.kage.protocol.BulkDismantleResponse;
import dev.kage.protocol.DestroyItemCommand;
import dev.kage.protocol.DestroyItemOutcome;
import dev.kage.protocol.DestroyItemResponse;
import dev.kage.protocol.EnhanceHistoryEntry;
import dev.kage.protocol.GetEnhanceHistoryResponse;
import dev.kage.protocol.GetMarketPriceHintRequest;
import dev.kage.protocol.GetMarketPriceHintResponse;

/** Lớp lệnh lô 38 (ADR-231…) gom thành MỘT thành phần của {@link ExpansionCommands}. */
record Lot38Commands(GatewayFacades gateway) {

    static Lot38Commands of(GatewayFacades gateway) {
        return new Lot38Commands(gateway);
    }

    /** ADR-231 — Virtual Thread; huỷ cả chồng một món. */
    DestroyItemResponse destroyItem(long characterId, DestroyItemCommand command) {
        return DestroyItemResponse.newBuilder()
                .setOutcome(DestroyItemOutcome.valueOf("DESTROY_ITEM_OUTCOME_"
                        + facades()
                                .itemDestroy()
                                .destroy(characterId, command.getItemId())
                                .name()))
                .build();
    }

    /** ADR-232 — Virtual Thread; phân giải mọi trang bị đủ điều kiện có cấp yêu cầu ≤ ngưỡng. */
    BulkDismantleResponse bulkDismantle(long characterId, BulkDismantleCommand command) {
        dev.kage.inventory.api.ItemHistoryFacade.BulkDismantle result = facades()
                .itemHistory()
                .dismantleAll(
                        characterId,
                        command.getMaxRequiredLevel(),
                        gateway.progression().daily().dismantle());
        return BulkDismantleResponse.newBuilder()
                .setDismantled(result.dismantled())
                .setGold(result.gold())
                .setHerbs(result.herbs())
                .build();
    }

    /** ADR-233 — Virtual Thread; 20 lần cường hoá gần nhất. */
    GetEnhanceHistoryResponse enhanceHistory(long characterId) {
        GetEnhanceHistoryResponse.Builder response = GetEnhanceHistoryResponse.newBuilder();
        for (ItemHistoryPort.EnhanceRecord entry : facades().itemHistory().enhanceHistory(characterId)) {
            response.addEntries(EnhanceHistoryEntry.newBuilder()
                    .setItemTemplateId(entry.itemTemplateId())
                    .setFromLevel(entry.fromLevel())
                    .setToLevel(entry.toLevel())
                    .setGoldCost(entry.goldCost())
                    .setResult(entry.result())
                    .setAtEpochSeconds(entry.at().getEpochSecond()));
        }
        return response.build();
    }

    /** ADR-234 — Virtual Thread; giá gợi ý của một mẫu vật phẩm. */
    GetMarketPriceHintResponse priceHint(GetMarketPriceHintRequest request) {
        MarketPriceHintPort.Hint hint = facades().priceHint().hint(request.getItemTemplateId(), Instant.now());
        return GetMarketPriceHintResponse.newBuilder()
                .setItemTemplateId(request.getItemTemplateId())
                .setLowestActive(hint.lowestActive())
                .setAverageSold(hint.averageSold())
                .setSoldListings(hint.soldListings())
                .build();
    }

    private Lot38Facades facades() {
        return gateway.progression().expansion().lot34().lot35().lot36().lot37().lot38();
    }
}
