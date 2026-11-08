package dev.kage.mail.api;

import java.util.Set;

import dev.kage.mail.domain.MailHousekeepingPort;

/** API dọn hộp thư: xoá hàng loạt thư đã xong (ADR-216), ghim thư (ADR-217). */
public final class MailHousekeepingFacade {

    /** Không có hộp thư (composition test). */
    public static final MailHousekeepingFacade DISABLED = new MailHousekeepingFacade(new MailHousekeepingPort() {
        @Override
        public int deleteFinished(long recipientId) {
            return 0;
        }

        @Override
        public boolean setPinned(long recipientId, long mailId, boolean pinned) {
            return false;
        }

        @Override
        public Set<Long> pinned(long recipientId) {
            return Set.of();
        }
    });

    private final MailHousekeepingPort port;

    /** Dựng ở composition root. */
    public MailHousekeepingFacade(MailHousekeepingPort port) {
        this.port = port;
    }

    /** Xoá mọi thư đã nhận / không đính kèm, trừ thư ghim; trả số thư đã xoá. */
    public int deleteFinished(long recipientId) {
        return port.deleteFinished(recipientId);
    }

    /** Ghim / bỏ ghim; {@code false} nếu không phải thư của người này. */
    public boolean setPinned(long recipientId, long mailId, boolean pinned) {
        return port.setPinned(recipientId, mailId, pinned);
    }

    /** Id các thư đang ghim. */
    public Set<Long> pinned(long recipientId) {
        return port.pinned(recipientId);
    }
}
