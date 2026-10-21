package dev.kage.gm.domain;

import java.time.Instant;

/**
 * Một sự kiện nhân đôi đang chạy (ADR-067).
 *
 * @param kind loại
 * @param multiplier hệ số, {@link #MIN_MULTIPLIER}..{@link #MAX_MULTIPLIER}
 * @param endsAt hết hiệu lực từ thời điểm này
 */
public record GameEvent(GameEventKind kind, int multiplier, Instant endsAt) {

    public static final int MIN_MULTIPLIER = 2;
    public static final int MAX_MULTIPLIER = 5;

    public GameEvent {
        if (kind == null || endsAt == null) {
            throw new IllegalArgumentException("kind/endsAt không được null");
        }
        if (multiplier < MIN_MULTIPLIER || multiplier > MAX_MULTIPLIER) {
            throw new IllegalArgumentException("multiplier ngoài biên: " + multiplier);
        }
    }

    /** Còn hiệu lực tại {@code now}. */
    public boolean activeAt(Instant now) {
        return now.isBefore(endsAt);
    }
}
