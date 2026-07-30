package dev.kage.shop.domain;

/**
 * Một dòng trong danh mục cửa hàng — giá tĩnh, đọc từ bảng {@code shop_item} (DATABASE.md §2.8).
 * {@code buyPrice}/{@code sellPrice} độc lập nhau (KHÔNG phải công thức % cố định) — chủ dự án
 * chọn tự cân bằng từng item thay vì áp 1 tỉ lệ chung (giống tinh thần {@code EnhancementTable}).
 */
public record ShopListing(long itemTemplateId, long buyPrice, long sellPrice) {

    public ShopListing {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (buyPrice < 0 || sellPrice < 0) {
            throw new IllegalArgumentException("Giá không được âm: buyPrice=" + buyPrice + ", sellPrice=" + sellPrice);
        }
    }
}
