package dev.kage.server.database.account;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.account.domain.LoginHistoryPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link LoginHistoryPort} (ADR-238, V167). Ghi + cắt bớt trong cùng một giao dịch. */
public final class PostgresLoginHistoryAdapter implements LoginHistoryPort {

    private static final String INSERT =
            "INSERT INTO login_history (account_id, masked_ip, logged_at) VALUES (?, ?, ?)";
    private static final String TRIM = """
            DELETE FROM login_history
             WHERE account_id = ?
               AND id NOT IN (SELECT id FROM login_history WHERE account_id = ? ORDER BY id DESC LIMIT ?)
            """;
    private static final String RECENT =
            "SELECT masked_ip, logged_at FROM login_history WHERE account_id = ? ORDER BY id DESC LIMIT ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLoginHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(long accountId, String maskedIp, Instant at, int keep) {
        JdbcTransactionRunner.call(dataSource, connection -> {
            JdbcOperations.update(connection, INSERT, accountId, maskedIp, Timestamp.from(at));
            return JdbcOperations.update(connection, TRIM, accountId, accountId, keep);
        });
    }

    @Override
    public List<Entry> recent(long accountId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getString("masked_ip"), rs.getTimestamp("logged_at").toInstant()),
                accountId,
                limit);
    }
}
