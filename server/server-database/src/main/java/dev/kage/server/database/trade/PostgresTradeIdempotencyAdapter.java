package dev.kage.server.database.trade;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.trade.domain.IdempotencyKeyReusedException;
import dev.kage.trade.domain.IdempotencyPort;
import dev.kage.trade.domain.Transaction;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.UUID;

/**
 * Hiện thực {@link IdempotencyPort} trên PostgreSQL — bảng {@code idempotency_key} DÙNG CHUNG
 * (V2, ADR-025), bản sao ý tưởng {@code PostgresShopIdempotencyAdapter}/{@code
 * PostgresMailIdempotencyAdapter}.
 */
public final class PostgresTradeIdempotencyAdapter implements IdempotencyPort {

    // Mã lỗi Postgres cho unique_violation (SQLState).
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private static final String INSERT_IDEMPOTENCY_KEY = """
            INSERT INTO idempotency_key (key, created_at) VALUES (?, ?)
            """;

    @Override
    public void recordOrThrow(Transaction tx, String idempotencyKey) {
        var connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_IDEMPOTENCY_KEY)) {
            statement.setObject(1, UUID.fromString(idempotencyKey), Types.OTHER);
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new IdempotencyKeyReusedException(idempotencyKey);
            }
            throw new DatabaseAccessException("Lỗi ghi idempotencyKey: " + idempotencyKey, e);
        }
    }
}
