package dev.kage.trade.application;

import java.util.HashSet;
import java.util.Set;

import dev.kage.trade.domain.IdempotencyKeyReusedException;
import dev.kage.trade.domain.IdempotencyPort;
import dev.kage.trade.domain.Transaction;

/** Fake in-memory THẬT — dùng chung cho test {@code application/}. */
final class FakeIdempotencyPort implements IdempotencyPort {

    private final Set<String> usedKeys = new HashSet<>();

    @Override
    public void recordOrThrow(Transaction tx, String idempotencyKey) {
        if (!usedKeys.add(idempotencyKey)) {
            throw new IdempotencyKeyReusedException(idempotencyKey);
        }
    }
}
