package dev.kage.server.database.mail;

import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.mail.domain.MailReceiptPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link MailReceiptPort} (ADR-400) — bảng {@code mail}. */
public final class PostgresMailReceiptAdapter implements MailReceiptPort {

    private static final String FIND =
            "SELECT sender_id, subject FROM mail WHERE id = ? AND recipient_id = ? AND sender_id IS NOT NULL";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresMailReceiptAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Receipt> receipt(long mailId, long recipientId) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND,
                rs -> new Receipt(rs.getLong("sender_id"), rs.getString("subject")),
                mailId,
                recipientId);
    }
}
