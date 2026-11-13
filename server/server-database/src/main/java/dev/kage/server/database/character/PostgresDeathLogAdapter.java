package dev.kage.server.database.character;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.DeathLogPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link DeathLogPort} (ADR-337) — audit {@code DEATH} (chỉ mục V177). */
public final class PostgresDeathLogAdapter implements DeathLogPort {

    private static final String INSERT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at)
            VALUES (?, 'DEATH', jsonb_build_object('killer', ?::text, 'monster', ?::text, 'x', ?::int)::text, now())
            """;
    private static final String RECENT = """
            SELECT detail_json::jsonb ->> 'killer' AS killer, detail_json::jsonb ->> 'monster' AS monster,
                   (detail_json::jsonb ->> 'x')::int AS x, created_at
              FROM audit_log WHERE character_id = ? AND action = 'DEATH' ORDER BY id DESC LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresDeathLogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(long characterId, Entry entry) {
        JdbcOperations.update(dataSource, INSERT, characterId, entry.killerName(), entry.monsterCode(), entry.x());
    }

    @Override
    public List<Entry> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getString("killer"),
                        rs.getString("monster"),
                        rs.getInt("x"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId,
                limit);
    }
}
