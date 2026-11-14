package dev.kage.mail.api;

import java.util.Set;

import dev.kage.mail.domain.MailReadPort;

/** API thư chưa đọc (ADR-314). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class MailReadFacade {

    /** Không lưu gì, mọi thư coi như đã đọc (composition test). */
    public static final MailReadFacade DISABLED = new MailReadFacade(new MailReadPort() {
        @Override
        public boolean markRead(long mailId, long recipientId) {
            return false;
        }

        @Override
        public Set<Long> unreadIds(long recipientId) {
            return Set.of();
        }

        @Override
        public int markAllRead(long recipientId) {
            return 0;
        }
    });

    private final MailReadPort port;

    /** Dựng ở composition root. */
    public MailReadFacade(MailReadPort port) {
        this.port = port;
    }

    /** Đánh dấu đã đọc. */
    public boolean markRead(long mailId, long recipientId) {
        return port.markRead(mailId, recipientId);
    }

    /** ADR-359 — đánh dấu tất cả đã đọc; trả số thư vừa đổi. */
    public int markAllRead(long recipientId) {
        return port.markAllRead(recipientId);
    }

    /** Id các thư chưa đọc. */
    public Set<Long> unreadIds(long recipientId) {
        return port.unreadIds(recipientId);
    }
}
