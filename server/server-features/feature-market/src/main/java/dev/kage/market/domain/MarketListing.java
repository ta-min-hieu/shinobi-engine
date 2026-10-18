package dev.kage.market.domain;

import java.time.Instant;

/**
 * Một dòng treo bán (bảng {@code market_listing}, ADR-063). Chỉ món chưa cường hoá (D2) nên không có cấp.
 *
 * @param price giá TỔNG cho cả {@code quantity}
 */
public record MarketListing(
        long id, long sellerId, String sellerName, long itemTemplateId, int quantity, long price, Instant expiresAt) {}
