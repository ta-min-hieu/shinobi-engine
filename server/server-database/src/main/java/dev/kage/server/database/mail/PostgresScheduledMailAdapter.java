package dev.kage.server.database.mail;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.mail.domain.ScheduledMailPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ScheduledMailPort} (ADR-297) — bảng {@code scheduled_mail} (V173). */
public final class PostgresScheduledMailAdapter implements ScheduledMailPort {

    private static final String COUNT = "SELECT COUNT(*) FROM scheduled_mail WHERE sender_id = ?";

    private static final String INSERT = """
            INSERT INTO scheduled_mail (sender_id, recipient_id, subject, body, send_at) VALUES (?, ?, ?, ?, ?)
            RETURNING id
            """;

    private static final String DUE = """
            SELECT id, sender_id, recipient_id, subject, body, send_at FROM scheduled_mail
             WHERE send_at <= ? ORDER BY send_at, id LIMIT ?
            """;

    private static final String DELETE = "DELETE FROM scheduled_mail WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresScheduledMailAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int pendingCount(long senderId) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), senderId)
                .orElse(0);
    }

    @Override
    public long schedule(Pending mail) {
        return JdbcOperations.queryOne(
                        dataSource,
                        INSERT,
                        rs -> rs.getLong(1),
                        mail.senderId(),
                        mail.recipientId(),
                        mail.subject(),
                        mail.body(),
                        Timestamp.from(mail.sendAt()))
                .orElse(0L);
    }

    @Override
    public List<Pending> due(Instant now, int limit) {
        return JdbcOperations.query(
                dataSource,
                DUE,
                rs -> new Pending(
                        rs.getLong("id"),
                        rs.getLong("sender_id"),
                        rs.getLong("recipient_id"),
                        rs.getString("subject"),
                        rs.getString("body"),
                        rs.getTimestamp("send_at").toInstant()),
                Timestamp.from(now),
                limit);
    }

    @Override
    public void delete(long id) {
        JdbcOperations.update(dataSource, DELETE, id);
    }
}
