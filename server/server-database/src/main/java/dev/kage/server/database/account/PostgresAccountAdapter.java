package dev.kage.server.database.account;

import dev.kage.account.domain.Account;
import dev.kage.account.domain.AccountPort;
import dev.kage.account.domain.Transaction;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/**
 * Hiện thực {@link AccountPort} trên PostgreSQL. SQL chỉ tồn tại ở đây (Điều 24 + DATABASE.md
 * §11). Luôn {@link PreparedStatement}, không nối chuỗi (SECURITY.md §11).
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
        var connection = ((JdbcTransaction) tx).connection();
        try {
            try (PreparedStatement upsert = connection.prepareStatement(UPSERT)) {
                upsert.setString(1, username);
                upsert.setTimestamp(2, Timestamp.from(Instant.now()));
                try (ResultSet rs = upsert.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            try (PreparedStatement select = connection.prepareStatement(FIND_BY_USERNAME)) {
                select.setString(1, username);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                    throw new IllegalStateException("Upsert conflict nhưng không tìm lại được: " + username);
                }
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi truy vấn account: " + username, e);
        }
    }

    private static Account mapRow(ResultSet rs) throws SQLException {
        return new Account(rs.getLong("id"), rs.getString("username"), rs.getTimestamp("created_at").toInstant());
    }
}
