package dev.kage.friend.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lời mời chờ trả lời trong RAM, có hạn (bái sư — ADR-093, cầu đạo lữ — ADR-094). Không ghi DB: lời mời chỉ có nghĩa
 * khi cả hai đang online, mất lúc khởi động lại là đúng ý. Khoá là CẶP {@code (người nhận, người gửi)} — một người gửi
 * nhiều lời cho nhiều người được, gửi lại cho cùng người thì gia hạn.
 */
public final class PendingOffers {

    private record Key(long recipientId, long senderId) {}

    private final Map<Key, Instant> expiries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public PendingOffers(Clock clock, Duration ttl) {
        this.clock = clock;
        this.ttl = ttl;
    }

    /** Ghi (hoặc gia hạn) lời của {@code senderId} gửi {@code recipientId}. */
    public void put(long recipientId, long senderId) {
        Instant now = clock.instant();
        expiries.values().removeIf(expiry -> !expiry.isAfter(now)); // dọn lời quá hạn — bảng không phình
        expiries.put(new Key(recipientId, senderId), now.plus(ttl));
    }

    /** Lấy RA lời đang chờ — {@code true} nếu có và chưa quá hạn (lời chỉ dùng được một lần). */
    public boolean take(long recipientId, long senderId) {
        Instant expiry = expiries.remove(new Key(recipientId, senderId));
        return expiry != null && expiry.isAfter(clock.instant());
    }
}
