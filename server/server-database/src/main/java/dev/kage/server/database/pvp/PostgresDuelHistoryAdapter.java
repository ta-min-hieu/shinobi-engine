package dev.kage.server.database.pvp;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.pvp.domain.DuelHistoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link DuelHistoryPort} (ADR-280) — audit {@code DUEL_RESULT} ⋈ tên đối thủ (đã xoá ⇒ "?"). */
public final class PostgresDuelHistoryAdapter implements DuelHistoryPort {

    private static final String RECENT = """
            SELECT COALESCE(c.name, '?') AS opponent,
                   (a.detail_json::jsonb ->> 'won')::boolean AS won,
                   (a.detail_json::jsonb ->> 'ratingAfter')::int AS rating_after,
                   a.created_at
              FROM audit_log a
              LEFT JOIN character c ON c.id = (a.detail_json::jsonb ->> 'opponentId')::bigint
             WHERE a.character_id = ? AND a.action = 'DUEL_RESULT'
             ORDER BY a.id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresDuelHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Duel> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Duel(
                        rs.getString("opponent"),
                        rs.getBoolean("won"),
                        rs.getInt("rating_after"),
                        rs.getTimestamp("created_at").toInstant()),
                characterId,
                limit);
    }
}
