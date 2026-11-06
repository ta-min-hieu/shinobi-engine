package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ADR-182 — "ai vừa đột phá" trong bộ nhớ, dùng chung mọi kết nối (người đột phá và người chúc mừng ở hai kết nối khác
 * nhau): lần đột phá gần nhất của từng nhân vật và ai đã chúc mừng lần đó. Không ghi DB — mất khi khởi động lại là
 * chấp nhận được (chỉ là lời chúc).
 */
final class FriendBreakthroughs {

    /** Chúc mừng được trong chừng này sau lần đột phá. */
    static final Duration WINDOW = Duration.ofMinutes(30);

    /** Kết quả một lần chúc mừng. */
    enum Outcome {
        OK,
        NO_RECENT_BREAKTHROUGH,
        ALREADY_CONGRATULATED
    }

    /** Lần đột phá gần nhất của một nhân vật. */
    private record Breakthrough(int realm, Instant at, Set<Long> congratulatedBy) {}

    /** Kết quả + cảnh giới vừa mở (khi OK). */
    record Congratulation(Outcome outcome, int realm) {}

    private final Map<Long, Breakthrough> recent = new ConcurrentHashMap<>();

    /** Ghi lần đột phá mới (thay lần cũ) và bỏ các lần đã quá {@link #WINDOW}. */
    void record(long characterId, int realm, Instant at) {
        recent.values().removeIf(old -> at.isAfter(old.at().plus(WINDOW)));
        recent.put(characterId, new Breakthrough(realm, at, ConcurrentHashMap.newKeySet()));
    }

    /** {@code fromId} chúc mừng {@code targetId} — một lần cho mỗi lần đột phá, trong {@link #WINDOW}. */
    Congratulation congratulate(long fromId, long targetId, Instant now) {
        Optional<Breakthrough> breakthrough =
                Optional.ofNullable(recent.get(targetId)).filter(b -> !now.isAfter(b.at().plus(WINDOW)));
        if (breakthrough.isEmpty()) {
            return new Congratulation(Outcome.NO_RECENT_BREAKTHROUGH, 0);
        }
        if (!breakthrough.get().congratulatedBy().add(fromId)) {
            return new Congratulation(
                    Outcome.ALREADY_CONGRATULATED, breakthrough.get().realm());
        }
        return new Congratulation(Outcome.OK, breakthrough.get().realm());
    }
}
