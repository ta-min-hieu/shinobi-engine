package dev.kage.server.database.account;

import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/**
 * Hiện thực {@link AccountPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link java.sql.PreparedStatement} qua {@link JdbcOperations}, không nối chuỗi
 * (SECURITY.md §11).
 */
public final class PostgresAccountAdapter implements AccountPort {

    // Upsert nguyên tử — tránh TOCTOU khi 2 request cùng tạo account trùng username đồng thời
    // (xem javadoc AccountPort.findOrCreate). Conflict → không có row trả về → SELECT lại.
    private static final String UPSERT = """
            INSERT INTO account (username, created_at)
            VALUES (?, ?)
            ON CONFLICT (username) DO NOTHING
            RETURNING id, username, created_at
            """;

    private static final String FIND_BY_USERNAME = """
            SELECT id, username, created_at FROM account WHERE username = ?
            """;

    @Override
    public Account findOrCreate(Transaction tx, String username) {
        Connection connection = ((JdbcTransaction) tx).connection();
        List<Account> upserted = JdbcOperations.query(
                connection, UPSERT, PostgresAccountAdapter::mapRow, username, Timestamp.from(Instant.now()));
        if (!upserted.isEmpty()) {
            return upserted.get(0);
        }
        return JdbcOperations.queryOne(connection, FIND_BY_USERNAME, PostgresAccountAdapter::mapRow, username)
                .orElseThrow(() -> new IllegalStateException("Upsert conflict nhưng không tìm lại được: " + username));
    }

    private static Account mapRow(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("username"), rs.getTimestamp("created_at").toInstant());
    }
}
