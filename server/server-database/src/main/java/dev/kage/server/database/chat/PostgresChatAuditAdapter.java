package dev.kage.server.database.chat;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.server.database.JdbcOperations;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;

/** Hiện thực {@link ChatAuditPort} trên PostgreSQL — ghi {@code audit_log} (DATABASE.md §2.7, V7). */
public final class PostgresChatAuditAdapter implements ChatAuditPort {

    private static final String INSERT_AUDIT_LOG = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresChatAuditAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void log(long characterId, String action, String detailJson) {
        JdbcOperations.update(
                dataSource, INSERT_AUDIT_LOG, characterId, action, detailJson, Timestamp.from(Instant.now()));
    }
}
