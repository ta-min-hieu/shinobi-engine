package dev.kage.server.database.party;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.party.domain.RecentPlayerPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link RecentPlayerPort} (ADR-329) — bảng {@code recent_player} (V176). */
public final class PostgresRecentPlayerAdapter implements RecentPlayerPort {

    private static final String UPSERT = """
            INSERT INTO recent_player (character_id, other_id, met_at) VALUES (?, ?, ?)
            ON CONFLICT (character_id, other_id) DO UPDATE SET met_at = EXCLUDED.met_at
            """;
    private static final String TRIM = """
            DELETE FROM recent_player WHERE character_id = ? AND other_id NOT IN (
                SELECT other_id FROM recent_player WHERE character_id = ? ORDER BY met_at DESC, other_id LIMIT ?)
            """;
    private static final String RECENT = """
            SELECT other_id, met_at FROM recent_player WHERE character_id = ? ORDER BY met_at DESC, other_id LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresRecentPlayerAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(long characterId, long otherId, Instant at, int keep) {
        JdbcOperations.update(dataSource, UPSERT, characterId, otherId, Timestamp.from(at));
        JdbcOperations.update(dataSource, TRIM, characterId, characterId, keep);
    }

    @Override
    public List<Entry> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getLong("other_id"), rs.getTimestamp("met_at").toInstant()),
                characterId,
                limit);
    }
}
