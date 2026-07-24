package dev.kage.trade.domain;

/** 1 dòng input khi đặt lời chào — KHÔNG xác thực sở hữu ở đây (xem {@link TradeSessionPort#replaceOffer}). */
public record OfferedItemInput(long itemId, int quantity) {

    public OfferedItemInput {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId phải > 0: " + itemId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
    }
}
