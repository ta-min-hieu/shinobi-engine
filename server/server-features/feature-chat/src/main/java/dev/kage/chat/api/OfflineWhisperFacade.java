package dev.kage.chat.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.chat.domain.OfflineWhisperPort;

/**
 * Nhắn riêng khi offline (ADR-187) — tối đa {@link #MAX_PENDING} tin chờ mỗi người nhận, giữ {@link #KEEP}. Tin đã qua
 * kiểm duyệt + audit của {@link ChatFacade#sendMessage} TRƯỚC khi tới đây. Chạm DB — chỉ gọi trên Virtual Thread.
 */
public final class OfflineWhisperFacade {

    /** Số tin chờ tối đa mỗi người nhận. PLACEHOLDER (Điều 27). */
    public static final int MAX_PENDING = 20;

    /** Tin chờ quá hạn này thì bỏ. */
    public static final Duration KEEP = Duration.ofDays(7);

    /** Composition không có hộp tin chờ (test): không giữ gì. */
    public static final OfflineWhisperFacade DISABLED = new OfflineWhisperFacade(new OfflineWhisperPort() {
        @Override
        public boolean store(long senderId, long recipientId, String body, Instant at, int maxPending) {
            return false;
        }

        @Override
        public List<Pending> takeAll(long recipientId, Instant since) {
            return List.of();
        }
    });

    private final OfflineWhisperPort port;

    /** Dựng ở composition root. */
    public OfflineWhisperFacade(OfflineWhisperPort port) {
        this.port = port;
    }

    /** Giữ một tin (đã kiểm duyệt); {@code false} nếu hộp người nhận đầy. */
    public boolean store(long senderId, long recipientId, String body, Instant at) {
        return port.store(senderId, recipientId, body, at, MAX_PENDING);
    }

    /** Lấy ra và xoá mọi tin chờ còn hạn của {@code recipientId}, cũ trước. */
    public List<OfflineWhisperPort.Pending> takeAll(long recipientId, Instant now) {
        return port.takeAll(recipientId, now.minus(KEEP));
    }
}
