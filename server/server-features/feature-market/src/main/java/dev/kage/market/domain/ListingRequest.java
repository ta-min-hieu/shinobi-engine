package dev.kage.market.domain;

/**
 * Lệnh treo bán từ người chơi (ADR-063) — gom để không vượt trần {@code ParameterNumber}.
 *
 * @param price giá TỔNG cả {@code quantity}
 * @param idempotencyKey khoá chống trùng ADR-025 (UUID)
 */
public record ListingRequest(long sellerId, long inventoryItemId, int quantity, long price, String idempotencyKey) {}
