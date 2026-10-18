package dev.kage.market.domain;

import java.time.Instant;

/** Dòng treo sắp ghi — món đã rời túi người bán (ký quỹ, ADR-063 D2). */
public record ListingDraft(long sellerId, long itemTemplateId, int quantity, long price, Instant expiresAt) {}
