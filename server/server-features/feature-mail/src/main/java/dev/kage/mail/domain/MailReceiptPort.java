package dev.kage.mail.domain;

import java.util.Optional;

/** Báo đã đọc thư (ADR-400) — đọc người gửi + tiêu đề của một thư. */
public interface MailReceiptPort {

    /** Người gửi (người chơi) và tiêu đề của thư. */
    record Receipt(long senderId, String subject) {}

    /** Thư {@code mailId} gửi tới {@code recipientId}, chỉ khi người gửi là người chơi (không phải thư hệ thống). */
    Optional<Receipt> receipt(long mailId, long recipientId);
}
