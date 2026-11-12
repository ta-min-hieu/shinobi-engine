package dev.kage.server.database.mail;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.mail.domain.SentMailPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link SentMailPort} (ADR-290) — {@code mail} theo {@code sender_id} ⋈ tên người nhận + số món đính kèm.
 */
public final class PostgresSentMailAdapter implements SentMailPort {

    private static final String RECENT = """
            SELECT COALESCE(c.name, '?') AS recipient, m.subject, m.gold_amount,
                   (SELECT COUNT(*) FROM mail_item mi WHERE mi.mail_id = m.id) AS attachments,
                   m.claimed_at IS NOT NULL AS claimed, m.created_at
              FROM mail m
              LEFT JOIN character c ON c.id = m.recipient_id
             WHERE m.sender_id = ?
             ORDER BY m.id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresSentMailAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Sent> recent(long senderId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Sent(
                        rs.getString("recipient"),
                        rs.getString("subject"),
                        rs.getLong("gold_amount"),
                        rs.getInt("attachments"),
                        rs.getBoolean("claimed"),
                        rs.getTimestamp("created_at").toInstant()),
                senderId,
                limit);
    }
}
