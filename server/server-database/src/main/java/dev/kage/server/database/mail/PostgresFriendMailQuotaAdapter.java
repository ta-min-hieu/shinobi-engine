package dev.kage.server.database.mail;

import java.sql.Timestamp;
import java.time.Instant;

import javax.sql.DataSource;

import dev.kage.mail.domain.FriendMailQuotaPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link FriendMailQuotaPort} (ADR-312) trên {@code audit_log} hành động {@code FRIEND_MAIL}. */
public final class PostgresFriendMailQuotaAdapter implements FriendMailQuotaPort {

    private static final String COUNT = """
            SELECT COUNT(*) FROM audit_log WHERE character_id = ? AND action = 'FRIEND_MAIL' AND created_at >= ?
            """;
    private static final String INSERT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, 'FRIEND_MAIL', ?, ?)
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresFriendMailQuotaAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int sentSince(long senderId, Instant since) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), senderId, Timestamp.from(since))
                .orElse(0);
    }

    @Override
    public void record(long senderId, int recipients, Instant at) {
        JdbcOperations.update(dataSource, INSERT, senderId, "{\"recipients\":" + recipients + "}", Timestamp.from(at));
    }
}
