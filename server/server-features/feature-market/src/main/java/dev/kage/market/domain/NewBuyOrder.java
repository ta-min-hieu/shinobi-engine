package dev.kage.market.domain;

import java.time.Instant;

/** Lệnh cầu mua sắp ghi (ADR-116) — gom tham số của {@link BuyOrderPort#insert}. */
public record NewBuyOrder(
        long buyerId, long itemTemplateId, int quantity, long unitPrice, Instant createdAt, Instant expiresAt) {}
