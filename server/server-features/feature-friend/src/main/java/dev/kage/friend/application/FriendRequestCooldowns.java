package dev.kage.friend.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ai vừa bị ai từ chối lời mời — để người bị từ chối không gửi lại ngay được (dòng 71(b), chủ dự án
 * duyệt 2026-10-17). Trước đó từ chối là xoá dòng PENDING và không để lại gì: bấm "mời" lại ngay lập
 * tức là một vòng quấy rối không có đáy.
 *
 * <p>Trong bộ nhớ, đúng một tiến trình (single-realm, ADR-010) — khởi động lại server là quên hết, chấp
 * nhận được cho một trần chống làm phiền. Một instance dùng CHUNG cho {@link SendFriendRequestUseCase} và
 * {@link RejectFriendRequestUseCase} (nối ở composition root); mỗi use case dựng bằng constructor một
 * tham số có sổ riêng — chỉ dành cho test không quan tâm tới luật này.
 */
public final class FriendRequestCooldowns {

    /** Thời gian chờ sau khi bị từ chối. */
    public static final Duration COOLDOWN = Duration.ofMinutes(10);

    private final Clock clock;
    private final Map<Pair, Instant> rejectedAt = new ConcurrentHashMap<>();

    public FriendRequestCooldowns() {
        this(Clock.systemUTC());
    }

    /** Constructor cho test — đồng hồ giả. */
    public FriendRequestCooldowns(Clock clock) {
        this.clock = clock;
    }

    /** {@code addresseeId} vừa từ chối lời mời của {@code requesterId}. */
    void recordRejection(long requesterId, long addresseeId) {
        Instant now = clock.instant();
        rejectedAt.values().removeIf(at -> !now.isBefore(at.plus(COOLDOWN)));
        rejectedAt.put(new Pair(requesterId, addresseeId), now);
    }

    /** {@code requesterId} còn phải chờ trước khi mời lại {@code addresseeId} không. */
    boolean isCoolingDown(long requesterId, long addresseeId) {
        Instant at = rejectedAt.get(new Pair(requesterId, addresseeId));
        return at != null && clock.instant().isBefore(at.plus(COOLDOWN));
    }

    private record Pair(long requesterId, long addresseeId) {}
}
