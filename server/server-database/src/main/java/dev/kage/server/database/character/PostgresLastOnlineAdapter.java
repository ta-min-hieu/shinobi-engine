package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.character.domain.LastOnlinePort;
import dev.kage.server.database.JdbcOperations;

/** ADR-192 — {@code character.last_online_at}. */
public final class PostgresLastOnlineAdapter implements LastOnlinePort {

    private static final String MARK = "UPDATE character SET last_online_at = ? WHERE id = ?";
    private static final String READ =
            "SELECT id, last_online_at FROM character WHERE id = ANY (?) AND last_online_at IS NOT NULL";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLastOnlineAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void markLeft(long characterId, Instant at) {
        JdbcOperations.update(dataSource, MARK, Timestamp.from(at), characterId);
    }

    @Override
    public Map<Long, Instant> lastOnlineOf(Collection<Long> characterIds) {
        Long[] ids = characterIds.toArray(Long[]::new);
        Map<Long, Instant> result = new HashMap<>();
        for (Map.Entry<Long, Instant> row : JdbcOperations.query(
                dataSource,
                READ,
                rs -> Map.entry(rs.getLong(1), rs.getTimestamp(2).toInstant()),
                (Object) ids)) {
            result.put(row.getKey(), row.getValue());
        }
        return result;
    }
}
