package dev.kage.mail.domain;

import java.time.Instant;
import java.util.List;

/** Thư đã gửi (ADR-290) — đọc {@code mail} theo {@code sender_id}. Thư người nhận đã xoá thì không còn. */
public interface SentMailPort {

    /** Một lá thư nhìn từ phía người gửi. {@code recipientName} "?" nếu nhân vật đã xoá. */
    record Sent(String recipientName, String subject, long goldAmount, int attachments, boolean claimed, Instant at) {}

    /** Tối đa {@code limit} thư gần nhất người này đã gửi, mới trước. */
    List<Sent> recent(long senderId, int limit);
}
