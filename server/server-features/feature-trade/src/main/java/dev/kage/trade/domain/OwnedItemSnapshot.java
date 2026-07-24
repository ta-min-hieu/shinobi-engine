package dev.kage.trade.domain;

/** 1 dòng {@code inventory_item} nhìn từ góc trade — đủ dữ liệu để hiển thị lời chào cho client. */
public record OwnedItemSnapshot(long itemId, long characterId, long itemTemplateId, int quantity, int enhanceLevel) {

    public OwnedItemSnapshot {
        if (itemId <= 0) {
            throw new IllegalArgumentException("itemId phải > 0: " + itemId);
        }
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
    }
}
