package dev.kage.trade.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ai vừa sửa lời chào của phiên nào, lúc nào — để đối phương KHÔNG xác nhận được ngay sau đó (dòng
 * 25(i)(b), chủ dự án duyệt 2026-10-17).
 *
 * <p>Đường lừa đảo nó chặn: B đang nhìn lời chào cũ và bấm xác nhận; A sửa lời chào đúng trước khi
 * lệnh của B tới. Sửa lời chào reset cả hai cờ xác nhận, nhưng lệnh của B tới SAU lần sửa, nên nó xác
 * nhận bản MỚI mà B chưa từng thấy. Chặn {@link #BLOCK_WINDOW} sau mỗi lần sửa cho client kịp nhận
 * {@code TradeUpdatedEvent} và vẽ lại. Còn khe nếu mạng của B trễ hơn khoảng đó — đóng hẳn cần
 * {@code offer_version} trên dây (25(i)(a), đổi protocol, cần ADR).
 *
 * <p>Chỉ chặn ĐỐI PHƯƠNG của người sửa: người tự sửa lời chào của mình rồi xác nhận ngay thì đã thấy
 * bản mình vừa gửi. Trong bộ nhớ, một tiến trình (single-realm, ADR-010) — khởi động lại server là
 * quên, vô hại vì phiên trade không sống qua lần khởi động lại có xác nhận dở. Cùng khuôn
 * {@code FriendRequestCooldowns}.
 */
public final class TradeOfferChanges {

    /** Khoảng đối phương không xác nhận được sau một lần sửa lời chào. */
    public static final Duration BLOCK_WINDOW = Duration.ofSeconds(3);

    private final Clock clock;
    private final Map<Long, Map<Long, Instant>> changedAtBySession = new ConcurrentHashMap<>();

    public TradeOfferChanges() {
        this(Clock.systemUTC());
    }

    /** Constructor cho test — đồng hồ giả. */
    public TradeOfferChanges(Clock clock) {
        this.clock = clock;
    }

    /** {@code changerId} vừa sửa lời chào của mình trong phiên {@code sessionId}. */
    void recordChange(long sessionId, long changerId) {
        Instant now = clock.instant();
        changedAtBySession.values().removeIf(byChanger -> {
            byChanger.values().removeIf(at -> isExpired(at, now));
            return byChanger.isEmpty();
        });
        changedAtBySession
                .computeIfAbsent(sessionId, id -> new ConcurrentHashMap<>())
                .put(changerId, now);
    }

    /** Đối phương của {@code characterId} có sửa lời chào trong {@link #BLOCK_WINDOW} vừa qua không. */
    boolean counterpartJustChanged(long sessionId, long characterId) {
        Map<Long, Instant> byChanger = changedAtBySession.get(sessionId);
        if (byChanger == null) {
            return false;
        }
        Instant now = clock.instant();
        for (Map.Entry<Long, Instant> entry : byChanger.entrySet()) {
            if (entry.getKey() != characterId && !isExpired(entry.getValue(), now)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isExpired(Instant changedAt, Instant now) {
        return !now.isBefore(changedAt.plus(BLOCK_WINDOW));
    }
}
