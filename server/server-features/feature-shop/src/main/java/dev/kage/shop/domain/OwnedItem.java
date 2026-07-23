package dev.kage.shop.domain;

/** 1 dòng {@code inventory_item} nhìn từ góc shop — chỉ mang đủ dữ liệu để định giá bán. */
public record OwnedItem(long itemId, long itemTemplateId, int quantity) {

    public OwnedItem {
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
