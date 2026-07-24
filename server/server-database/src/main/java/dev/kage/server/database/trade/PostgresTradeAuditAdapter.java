package dev.kage.server.database.trade;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeAuditPort;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;

/** Hiện thực {@link TradeAuditPort} trên PostgreSQL — ghi {@code audit_log} (DATABASE.md §2.7, V7). */
public final class PostgresTradeAuditAdapter implements TradeAuditPort {

    private static final String INSERT_AUDIT_LOG = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at) VALUES (?, ?, ?, ?)
            """;

    @Override
    public void log(Transaction tx, long characterId, String action, String detailJson) {
        var connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_AUDIT_LOG)) {
            statement.setLong(1, characterId);
            statement.setString(2, action);
            statement.setString(3, detailJson);
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi ghi audit_log: characterId=" + characterId + ", action=" + action, e);
        }
    }
}
