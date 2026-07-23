package dev.kage.server.database.chat;

import dev.kage.chat.domain.ChatAuditPort;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.SQLException;
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
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(INSERT_AUDIT_LOG)) {
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
