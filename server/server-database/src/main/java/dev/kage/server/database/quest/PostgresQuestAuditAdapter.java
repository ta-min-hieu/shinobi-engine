package dev.kage.server.database.quest;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;

/** Hiện thực {@link AuditPort} trên PostgreSQL — ghi {@code audit_log} (DATABASE.md §2.7, V7). */
public final class PostgresQuestAuditAdapter implements AuditPort {

    private static final String INSERT_AUDIT_LOG = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    @Override
    public void log(Transaction tx, long characterId, String action, String detailJson) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(
                connection, INSERT_AUDIT_LOG, characterId, action, detailJson, Timestamp.from(Instant.now()));
    }
}
