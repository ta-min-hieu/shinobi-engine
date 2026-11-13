package dev.kage.server.database.character;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.BreakthroughLogPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link BreakthroughLogPort} (ADR-322) — audit {@code BREAKTHROUGH} (chỉ mục V175). */
public final class PostgresBreakthroughLogAdapter implements BreakthroughLogPort {

    private static final String INSERT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at)
            VALUES (?, 'BREAKTHROUGH', jsonb_build_object('realm', ?::int, 'level', ?::int)::text, now())
            """;
    private static final String HISTORY = """
            SELECT (detail_json::jsonb ->> 'realm')::int AS realm, (detail_json::jsonb ->> 'level')::int AS level,
                   created_at
              FROM audit_log WHERE character_id = ? AND action = 'BREAKTHROUGH' ORDER BY id
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresBreakthroughLogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(long characterId, int realm, int level) {
        JdbcOperations.update(dataSource, INSERT, characterId, realm, level);
    }

    @Override
    public List<Entry> history(long characterId) {
        return JdbcOperations.query(
                dataSource,
                HISTORY,
                rs -> new Entry(
                        rs.getInt("realm"),
                        rs.getInt("level"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId);
    }
}
