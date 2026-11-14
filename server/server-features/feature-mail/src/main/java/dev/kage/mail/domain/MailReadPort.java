package dev.kage.mail.domain;

import java.util.Set;

/** Thư chưa đọc (ADR-314) — cột {@code mail.read_at}. */
public interface MailReadPort {

    /** Đánh dấu đã đọc một thư của chính người nhận; {@code false} nếu không phải thư của họ. */
    boolean markRead(long mailId, long recipientId);

    /** Id các thư CHƯA đọc của người nhận. */
    Set<Long> unreadIds(long recipientId);

    /** ADR-359 — đánh dấu mọi thư chưa đọc của người nhận là đã đọc; trả số thư vừa đổi. */
    int markAllRead(long recipientId);
}
