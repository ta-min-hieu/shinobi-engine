package dev.kage.mail.api;

import java.util.List;

import dev.kage.mail.domain.MailArchivePort;

/**
 * API lưu trữ thư (ADR-354): thư đã cất không hiện ở hộp chính, không bị xoá hàng loạt (ADR-216) và coi như đã đọc.
 * Chạm DB — Virtual Thread.
 */
public final class MailArchiveFacade {

    /** Không lưu gì (composition test). */
    public static final MailArchiveFacade DISABLED = new MailArchiveFacade(new MailArchivePort() {
        @Override
        public boolean setArchived(long mailId, long ownerId, boolean archived) {
            return false;
        }

        @Override
        public List<Entry> archived(long ownerId) {
            return List.of();
        }
    });

    private final MailArchivePort port;

    /** Dựng ở composition root. */
    public MailArchiveFacade(MailArchivePort port) {
        this.port = port;
    }

    /** Cất / trả về; {@code false} nếu không phải thư của mình. */
    public boolean setArchived(long mailId, long ownerId, boolean archived) {
        return port.setArchived(mailId, ownerId, archived);
    }

    /** Kho lưu trữ, mới trước. */
    public List<MailArchivePort.Entry> archived(long ownerId) {
        return port.archived(ownerId);
    }
}
