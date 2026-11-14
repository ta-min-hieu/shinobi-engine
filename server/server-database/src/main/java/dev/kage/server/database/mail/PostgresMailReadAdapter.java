package dev.kage.server.database.mail;

import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import dev.kage.mail.domain.MailReadPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MailReadPort} (ADR-314) — cột {@code mail.read_at} (V174). */
public final class PostgresMailReadAdapter implements MailReadPort {

    private static final String MARK = """
            UPDATE mail SET read_at = COALESCE(read_at, now()) WHERE id = ? AND recipient_id = ?
            """;
    private static final String UNREAD = "SELECT id FROM mail WHERE recipient_id = ? AND read_at IS NULL";
    private static final String MARK_ALL = "UPDATE mail SET read_at = now() WHERE recipient_id = ? AND read_at IS NULL";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMailReadAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean markRead(long mailId, long recipientId) {
        return JdbcOperations.update(dataSource, MARK, mailId, recipientId) == 1;
    }

    @Override
    public int markAllRead(long recipientId) {
        return JdbcOperations.update(dataSource, MARK_ALL, recipientId);
    }

    @Override
    public Set<Long> unreadIds(long recipientId) {
        return new HashSet<>(JdbcOperations.query(dataSource, UNREAD, rs -> rs.getLong(1), recipientId));
    }
}
