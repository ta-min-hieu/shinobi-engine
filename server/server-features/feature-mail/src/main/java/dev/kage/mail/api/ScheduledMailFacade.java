package dev.kage.mail.api;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import dev.kage.mail.domain.ScheduledMailPort;

/**
 * API thư hẹn giờ (ADR-297): hẹn {@value #MIN_DELAY_MINUTES}–{@value #MAX_DELAY_MINUTES} phút, tối đa
 * {@value #MAX_PENDING} thư đang chờ mỗi người. Chỉ chữ (không vàng, không đồ) — tới giờ đi qua đường gửi thư thường
 * (tem thư, chặn người lạ, hộp đầy áp dụng LÚC GỬI). Chạm DB — chỉ gọi trên Virtual Thread / luồng nền.
 */
public final class ScheduledMailFacade {

    /** Hẹn sớm nhất. */
    public static final int MIN_DELAY_MINUTES = 1;

    /** Hẹn muộn nhất (7 ngày). */
    public static final int MAX_DELAY_MINUTES = 10_080;

    /** Số thư đang chờ tối đa mỗi người. */
    public static final int MAX_PENDING = 5;

    /** Mỗi lượt quét gửi tối đa bấy nhiêu thư. */
    public static final int BATCH = 100;

    /** Kết quả hẹn. */
    public enum Outcome {
        OK,
        INVALID_DELAY,
        TOO_MANY_PENDING
    }

    /** Không lưu gì (composition test). */
    public static final ScheduledMailFacade DISABLED = new ScheduledMailFacade(new ScheduledMailPort() {
        @Override
        public int pendingCount(long senderId) {
            return 0;
        }

        @Override
        public long schedule(Pending mail) {
            return 0L;
        }

        @Override
        public List<Pending> due(Instant now, int limit) {
            return List.of();
        }

        @Override
        public void delete(long id) {
            // không có gì để xoá
        }
    });

    private final ScheduledMailPort port;

    /** Dựng ở composition root. */
    public ScheduledMailFacade(ScheduledMailPort port) {
        this.port = port;
    }

    /** Hẹn một thư (độ dài tiêu đề/nội dung do người gọi đã kiểm như thư thường). */
    public Outcome schedule(ScheduledMailPort.Pending mail, int delayMinutes, Instant now) {
        if (delayMinutes < MIN_DELAY_MINUTES || delayMinutes > MAX_DELAY_MINUTES) {
            return Outcome.INVALID_DELAY;
        }
        if (port.pendingCount(mail.senderId()) >= MAX_PENDING) {
            return Outcome.TOO_MANY_PENDING;
        }
        port.schedule(new ScheduledMailPort.Pending(
                0L,
                mail.senderId(),
                mail.recipientId(),
                mail.subject(),
                mail.body(),
                now.plus(Duration.ofMinutes(delayMinutes))));
        return Outcome.OK;
    }

    /** Số thư đang chờ của {@code senderId}. */
    public int pendingCount(long senderId) {
        return port.pendingCount(senderId);
    }

    /** Thư đã tới giờ. */
    public List<ScheduledMailPort.Pending> due(Instant now) {
        return port.due(now, BATCH);
    }

    /** Xoá sau khi đã xử lý. */
    public void delete(long id) {
        port.delete(id);
    }
}
