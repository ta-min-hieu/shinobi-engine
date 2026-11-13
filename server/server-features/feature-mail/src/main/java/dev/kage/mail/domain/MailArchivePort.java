package dev.kage.mail.domain;

import java.time.Instant;
import java.util.List;

/** Lưu trữ thư (ADR-354) — cột {@code mail.archived_at}. */
public interface MailArchivePort {

    /** Một thư trong kho lưu trữ. {@code senderName} rỗng = thư hệ thống. */
    record Entry(long mailId, String senderName, String subject, Instant sentAt) {}

    /** Cất ({@code true}) hoặc trả thư về hộp chính; {@code false} nếu không phải thư của mình. */
    boolean setArchived(long mailId, long ownerId, boolean archived);

    /** Thư đã lưu trữ, mới trước. */
    List<Entry> archived(long ownerId);
}
