package dev.kage.server.database.monster;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.monster.domain.BossKillLogPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link BossKillLogPort} (ADR-296) — audit {@code BOSS_KILL}, mọi người tham gia một trận trong một giao
 * dịch.
 */
public final class PostgresBossKillLogAdapter implements BossKillLogPort {

    private static final String INSERT = """
            INSERT INTO audit_log (character_id, action, detail_json, created_at)
            VALUES (?, 'BOSS_KILL', jsonb_build_object('boss', ?::text, 'kind', ?::text, 'rank', ?::int,
                    'damage', ?::bigint)::text, now())
            """;

    private static final String RECENT = """
            SELECT detail_json::jsonb ->> 'boss' AS boss, detail_json::jsonb ->> 'kind' AS kind,
                   (detail_json::jsonb ->> 'rank')::int AS rank, (detail_json::jsonb ->> 'damage')::bigint AS damage,
                   created_at
              FROM audit_log
             WHERE character_id = ? AND action = 'BOSS_KILL'
             ORDER BY id DESC
             LIMIT ?
            """;

    private static final String TOP_WORLD = """
            SELECT COALESCE(c.name, '?') AS name, SUM((a.detail_json::jsonb ->> 'damage')::bigint) AS total, COUNT(*) AS kills
              FROM audit_log a
              LEFT JOIN character c ON c.id = a.character_id
             WHERE a.action = 'BOSS_KILL' AND a.detail_json::jsonb ->> 'kind' = 'WORLD'
             GROUP BY a.character_id, c.name
             ORDER BY total DESC, name
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresBossKillLogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(List<Kill> kills) {
        JdbcTransactionRunner.run(dataSource, connection -> {
            for (Kill kill : kills) {
                JdbcOperations.update(
                        connection,
                        INSERT,
                        kill.characterId(),
                        kill.bossCode(),
                        kill.kind().name(),
                        kill.rank(),
                        kill.damage());
            }
        });
    }

    @Override
    public List<Ranked> topWorldDamage(int limit) {
        return JdbcOperations.query(
                dataSource,
                TOP_WORLD,
                rs -> new Ranked(rs.getString("name"), rs.getLong("total"), rs.getInt("kills")),
                limit);
    }

    @Override
    public List<Entry> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getString("boss"),
                        Kind.valueOf(rs.getString("kind")),
                        rs.getInt("rank"),
                        rs.getLong("damage"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId,
                limit);
    }
}
