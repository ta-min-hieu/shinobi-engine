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

    // ADR-303 — tổng sát thương lấy từ sổ world_boss_damage (ADR-081, cộng dồn từ khi có boss thế giới — cùng nguồn với
    // thẻ BOSS_DAMAGE của Bia Anh Danh); số trận đếm từ audit BOSS_KILL (chỉ có từ ADR-296).
    private static final String TOP_WORLD = """
            SELECT COALESCE(c.name, '?') AS name, d.total_damage AS total,
                   (SELECT COUNT(*) FROM audit_log a
                     WHERE a.character_id = d.character_id AND a.action = 'BOSS_KILL'
                       AND a.detail_json::jsonb ->> 'kind' = 'WORLD') AS kills
              FROM world_boss_damage d
              LEFT JOIN character c ON c.id = d.character_id
             ORDER BY d.total_damage DESC, d.character_id
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
