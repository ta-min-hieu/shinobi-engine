package dev.kage.trade.domain;

import java.time.Instant;
import java.util.Objects;

/** Aggregate root — danh tính 1 phiên trade (KHÔNG mang gold/offer/confirm, xem {@link TradeSessionSnapshot}). */
public record TradeSession(
        long id, long requesterCharacterId, long targetCharacterId, TradeStatus status, Instant createdAt) {

    public TradeSession {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        if (requesterCharacterId == targetCharacterId) {
            throw new IllegalArgumentException(
                    "requesterCharacterId và targetCharacterId không được trùng: " + requesterCharacterId);
        }
    }

    public boolean involves(long characterId) {
        return characterId == requesterCharacterId || characterId == targetCharacterId;
    }
}
