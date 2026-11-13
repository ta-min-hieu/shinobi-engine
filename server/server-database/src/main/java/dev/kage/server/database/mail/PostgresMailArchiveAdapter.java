package dev.kage.server.database.mail;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.mail.domain.MailArchivePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MailArchivePort} (ADR-354) — cột {@code mail.archived_at} (V179). Cất thư cũng đánh dấu đã đọc. */
public final class PostgresMailArchiveAdapter implements MailArchivePort {

    private static final String ARCHIVE = """
            UPDATE mail SET archived_at = now(), read_at = COALESCE(read_at, now()) WHERE id = ? AND recipient_id = ?
            """;
    private static final String RESTORE = "UPDATE mail SET archived_at = NULL WHERE id = ? AND recipient_id = ?";
    private static final String ARCHIVED = """
            SELECT m.id, COALESCE(c.name, '') AS sender_name, m.subject, m.created_at
              FROM mail m LEFT JOIN character c ON c.id = m.sender_id
             WHERE m.recipient_id = ? AND m.archived_at IS NOT NULL ORDER BY m.created_at DESC
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMailArchiveAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean setArchived(long mailId, long ownerId, boolean archived) {
        return JdbcOperations.update(dataSource, archived ? ARCHIVE : RESTORE, mailId, ownerId) == 1;
    }

    @Override
    public List<Entry> archived(long ownerId) {
        return JdbcOperations.query(
                dataSource,
                ARCHIVED,
                rs -> new Entry(
                        rs.getLong("id"),
                        rs.getString("sender_name"),
                        rs.getString("subject"),
                        rs.getTimestamp("created_at").toInstant()),
                ownerId);
    }
}
