package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.ProfileVisitPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link ProfileVisitPort} (lô 54) — đọc {@code profile_like} (V173), ghi {@code inspect_log} và
 * {@code inspect_friends_only} (V180).
 */
public final class PostgresProfileVisitAdapter implements ProfileVisitPort {

    private static final String LIKERS = """
            SELECT c.name, MAX(l.liked_on) AS last_day FROM profile_like l JOIN character c ON c.id = l.liker_id
             WHERE l.target_id = ? GROUP BY c.name ORDER BY last_day DESC, c.name LIMIT ?
            """;
    private static final String INSERT_VISIT =
            "INSERT INTO inspect_log (target_id, viewer_id, inspected_at) VALUES (?, ?, ?)";
    private static final String TRIM_VISITS = """
            DELETE FROM inspect_log WHERE target_id = ? AND id NOT IN (
                SELECT id FROM inspect_log WHERE target_id = ? ORDER BY id DESC LIMIT ?)
            """;
    private static final String VISITS = """
            SELECT c.name, v.inspected_at FROM inspect_log v JOIN character c ON c.id = v.viewer_id
             WHERE v.target_id = ? ORDER BY v.id DESC LIMIT ?
            """;
    private static final String FRIENDS_ONLY = "SELECT 1 FROM inspect_friends_only WHERE character_id = ?";
    private static final String ENABLE =
            "INSERT INTO inspect_friends_only (character_id) VALUES (?) ON CONFLICT DO NOTHING";
    private static final String DISABLE = "DELETE FROM inspect_friends_only WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresProfileVisitAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Liker> recentLikers(long targetId, int limit) {
        return JdbcOperations.query(
                dataSource,
                LIKERS,
                rs -> new Liker(rs.getString("name"), rs.getObject("last_day", LocalDate.class)),
                targetId,
                limit);
    }

    @Override
    public void recordInspect(long targetId, long viewerId, Instant at, int keep) {
        JdbcOperations.update(dataSource, INSERT_VISIT, targetId, viewerId, Timestamp.from(at));
        JdbcOperations.update(dataSource, TRIM_VISITS, targetId, targetId, keep);
    }

    @Override
    public List<Visit> inspectHistory(long targetId, int limit) {
        return JdbcOperations.query(
                dataSource,
                VISITS,
                rs -> new Visit(
                        rs.getString("name"), rs.getTimestamp("inspected_at").toInstant()),
                targetId,
                limit);
    }

    @Override
    public boolean friendsOnly(long characterId) {
        return JdbcOperations.queryOne(dataSource, FRIENDS_ONLY, rs -> rs.getInt(1), characterId)
                .isPresent();
    }

    @Override
    public void setFriendsOnly(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, enabled ? ENABLE : DISABLE, characterId);
    }
}
