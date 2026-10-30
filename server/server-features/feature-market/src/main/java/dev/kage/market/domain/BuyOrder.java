package dev.kage.market.domain;

import java.time.Instant;

/**
 * Một lệnh cầu mua (ADR-116): người mua ký quỹ {@code quantityWanted × unitPrice} linh thạch lúc đặt; mỗi lần bán vào
 * lệnh trả người bán từ ký quỹ.
 */
public record BuyOrder(
        long id,
        long buyerId,
        long itemTemplateId,
        int quantityWanted,
        int quantityFilled,
        long unitPrice,
        Instant expiresAt) {

    /** Số còn cần mua. */
    public int remaining() {
        return quantityWanted - quantityFilled;
    }

    /** Ký quỹ còn giữ. */
    public long escrowRemaining() {
        return (long) remaining() * unitPrice;
    }
}
