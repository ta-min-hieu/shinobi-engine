package dev.kage.mail.api;

import java.util.Optional;

import dev.kage.mail.domain.MailReceiptPort;

/** API báo đã đọc thư (ADR-400). Chạm DB — Virtual Thread. */
public final class MailReceiptFacade {

    /** Không có thư nào (composition test). */
    public static final MailReceiptFacade DISABLED = new MailReceiptFacade((mailId, recipientId) -> Optional.empty());

    private final MailReceiptPort port;

    /** Dựng ở composition root. */
    public MailReceiptFacade(MailReceiptPort port) {
        this.port = port;
    }

    /** Người gửi + tiêu đề nếu thư do người chơi gửi. */
    public Optional<MailReceiptPort.Receipt> receipt(long mailId, long recipientId) {
        return port.receipt(mailId, recipientId);
    }
}
