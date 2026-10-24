package dev.kage.server.gateway;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Giờ vào world của từng nhân vật đang chơi (ADR-096) — RAM, dùng chung mọi kết nối. Giây online hôm nay = đã lưu
 * trong DB + phần của phiên này; lúc rời world mới cộng vào DB.
 */
final class OnlineSessions {

    private final Map<Long, Instant> enteredAt = new ConcurrentHashMap<>();

    void enter(long characterId, Instant now) {
        enteredAt.put(characterId, now);
    }

    /** Lấy ra giờ vào của phiên vừa kết thúc; rỗng nếu không có (đã lấy rồi). */
    Optional<Instant> leave(long characterId) {
        return Optional.ofNullable(enteredAt.remove(characterId));
    }

    /** Giờ vào của phiên đang chạy; {@code fallback} nếu chưa ghi (chưa vào world). */
    Instant enteredAt(long characterId, Instant fallback) {
        return enteredAt.getOrDefault(characterId, fallback);
    }
}
