package dev.kage.mail.domain;

import java.time.Instant;
import java.util.List;

/** Thư hẹn giờ (ADR-297) — bảng {@code scheduled_mail}: giữ thư thường tới giờ hẹn. */
public interface ScheduledMailPort {

    /** Một thư đang chờ. */
    record Pending(long id, long senderId, long recipientId, String subject, String body, Instant sendAt) {}

    /** Số thư người này đang hẹn. */
    int pendingCount(long senderId);

    /** Lưu một thư hẹn; trả id. */
    long schedule(Pending mail);

    /** Các thư đã tới giờ (tối đa {@code limit}), cũ trước. */
    List<Pending> due(Instant now, int limit);

    /** Xoá sau khi đã gửi (hoặc bỏ). */
    void delete(long id);
}
