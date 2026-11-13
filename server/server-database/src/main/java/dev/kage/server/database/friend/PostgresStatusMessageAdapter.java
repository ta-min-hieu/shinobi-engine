package dev.kage.server.database.friend;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.StatusMessagePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link StatusMessagePort} (ADR-335) — bảng {@code status_message} (V177). */
public final class PostgresStatusMessageAdapter implements StatusMessagePort {

    private static final String MANY = "SELECT character_id, message FROM status_message WHERE character_id = ANY (?)";
    private static final String ONE = "SELECT message FROM status_message WHERE character_id = ?";
    private static final String UPSERT = """
            INSERT INTO status_message (character_id, message) VALUES (?, ?)
            ON CONFLICT (character_id) DO UPDATE SET message = EXCLUDED.message
            """;
    private static final String DELETE = "DELETE FROM status_message WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresStatusMessageAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, String> messagesOf(Collection<Long> characterIds) {
        Map<Long, String> out = new HashMap<>();
        JdbcOperations.query(
                        dataSource, MANY, rs -> Map.entry(rs.getLong("character_id"), rs.getString("message")), (Object)
                                characterIds.toArray(Long[]::new))
                .forEach(entry -> out.put(entry.getKey(), entry.getValue()));
        return out;
    }

    @Override
    public Optional<String> messageOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, ONE, rs -> rs.getString(1), characterId);
    }

    @Override
    public void set(long characterId, String message) {
        if (message.isEmpty()) {
            JdbcOperations.update(dataSource, DELETE, characterId);
        } else {
            JdbcOperations.update(dataSource, UPSERT, characterId, message);
        }
    }
}
