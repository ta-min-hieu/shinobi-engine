package dev.kage.server.database.chat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.chat.domain.ChatMutePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ChatMutePort} trên {@code chat_mute} (V61, ADR-052 D4). */
public final class PostgresChatMuteAdapter implements ChatMutePort {

    private static final String FIND = "SELECT muted_until FROM chat_mute WHERE account_id = ?";

    private static final String UPSERT = """
            INSERT INTO chat_mute (account_id, muted_until, gm_audit_log_id) VALUES (?, ?, ?)
            ON CONFLICT (account_id) DO UPDATE SET muted_until = EXCLUDED.muted_until,
                                                   gm_audit_log_id = EXCLUDED.gm_audit_log_id
            """;

    private static final String DELETE = "DELETE FROM chat_mute WHERE account_id = ?";

    private final DataSource dataSource;

    public PostgresChatMuteAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Instant> mutedUntil(long accountId) {
        return JdbcOperations.queryOne(
                dataSource, FIND, rs -> rs.getTimestamp("muted_until").toInstant(), accountId);
    }

    @Override
    public void mute(long accountId, Instant until, long auditId) {
        JdbcOperations.update(dataSource, UPSERT, accountId, Timestamp.from(until), auditId);
    }

    @Override
    public boolean unmute(long accountId) {
        return JdbcOperations.update(dataSource, DELETE, accountId) == 1;
    }
}
