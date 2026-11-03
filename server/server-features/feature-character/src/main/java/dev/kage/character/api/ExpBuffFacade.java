package dev.kage.character.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import dev.kage.character.domain.ExpBuffPort;

/**
 * Đan x2 kinh nghiệm (ADR-164). EXP đánh quái đi qua {@link #boosted} MỖI lần hạ quái, nên hạn được nhớ trong RAM (một
 * lần đọc DB mỗi nhân vật); dùng đan xong gateway gọi {@link #refresh} để đọc lại.
 */
public final class ExpBuffFacade {

    /** Composition không có đan x2 EXP (test): không ai được nhân đôi. */
    public static final ExpBuffFacade DISABLED = new ExpBuffFacade(characterId -> Optional.empty());

    private static final long DOUBLE = 2L;

    private final ExpBuffPort port;
    private final Map<Long, Instant> expiries = new ConcurrentHashMap<>();

    /** Dựng ở composition root. */
    public ExpBuffFacade(ExpBuffPort port) {
        this.port = port;
    }

    /** EXP sau x2 nếu còn hạn lúc {@code now}. */
    public long boosted(long characterId, long amount, Instant now) {
        return now.isBefore(expiry(characterId)) ? amount * DOUBLE : amount;
    }

    /** Số giây x2 còn lại (0 nếu hết). */
    public long secondsLeft(long characterId, Instant now) {
        Instant expiry = expiry(characterId);
        return now.isBefore(expiry) ? Duration.between(now, expiry).toSeconds() : 0L;
    }

    /** Đọc lại hạn từ DB — gọi sau khi vừa dùng đan. */
    public void refresh(long characterId) {
        expiries.put(characterId, port.expiresAt(characterId).orElse(Instant.EPOCH));
    }

    private Instant expiry(long characterId) {
        return expiries.computeIfAbsent(characterId, id -> port.expiresAt(id).orElse(Instant.EPOCH));
    }
}
