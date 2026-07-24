package dev.kage.trade.domain;

import java.util.List;
import java.util.Objects;

/**
 * Toàn bộ trạng thái 1 phiên trade tại 1 thời điểm — dùng CẢ cho xem trạng thái (đọc thuần) LẪN
 * thi hành (đọc có khoá {@code FOR UPDATE}, xem {@link TradeSessionPort#lockForExecution}).
 */
public record TradeSessionSnapshot(
        long id,
        long requesterCharacterId,
        long targetCharacterId,
        TradeStatus status,
        long requesterGold,
        long targetGold,
        boolean requesterConfirmed,
        boolean targetConfirmed,
        List<TradeOfferItem> items) {

    public TradeSessionSnapshot {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(items, "items");
        items = List.copyOf(items);
    }

    public boolean involves(long characterId) {
        return characterId == requesterCharacterId || characterId == targetCharacterId;
    }

    public boolean bothConfirmed() {
        return requesterConfirmed && targetConfirmed;
    }

    public List<TradeOfferItem> itemsOffered(long offeringCharacterId) {
        return items.stream().filter(i -> i.offeringCharacterId() == offeringCharacterId).toList();
    }
}
