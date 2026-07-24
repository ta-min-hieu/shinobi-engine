package dev.kage.trade.domain;

/**
 * 1 item trong lời chào của {@code offeringCharacterId}. {@code itemId} là tham chiếu MỀM tới
 * {@code inventory_item.id} — KHÔNG khoá/kiểm ngay lúc chào, xác thực lại lúc thi hành (xem
 * migration V16, feature-trade/CLAUDE.md).
 */
public record TradeOfferItem(long id, long sessionId, long offeringCharacterId, long itemId, int quantity) {

    public TradeOfferItem {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId phải > 0: " + itemId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
    }
}
