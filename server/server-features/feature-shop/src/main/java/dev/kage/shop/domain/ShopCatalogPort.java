package dev.kage.shop.domain;

import java.util.List;
import java.util.Optional;

/** Cổng đọc danh mục cửa hàng ({@code shop_item}) — PHÁT ĐỌC THUẦN, không ghi (config tĩnh). */
public interface ShopCatalogPort {

    /** {@code Optional.empty()} nếu {@code itemTemplateId} không bán/mua ở shop. */
    Optional<ShopListing> findListing(long itemTemplateId);

    /** Toàn bộ danh mục — dùng cho client hỏi "shop có gì" trước khi mua. */
    List<ShopListing> findAll();
}
