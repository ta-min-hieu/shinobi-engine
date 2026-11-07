package dev.kage.server.database.pvp;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.pvp.domain.EnemyListPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** ADR-194 — {@code pvp_enemy}: ghi + cắt trong một transaction; đọc kèm tên nhân vật. */
public final class PostgresEnemyListAdapter implements EnemyListPort {

    private static final String UPSERT = """
            INSERT INTO pvp_enemy (character_id, enemy_character_id, kill_count, last_killed_at)
            VALUES (?, ?, 1, ?)
            ON CONFLICT (character_id, enemy_character_id)
            DO UPDATE SET kill_count = pvp_enemy.kill_count + 1, last_killed_at = EXCLUDED.last_killed_at
            """;

    private static final String TRIM = """
            DELETE FROM pvp_enemy
             WHERE character_id = ?
               AND enemy_character_id NOT IN (
                   SELECT enemy_character_id FROM pvp_enemy
                    WHERE character_id = ?
                    ORDER BY last_killed_at DESC, enemy_character_id DESC
                    LIMIT ?)
            """;

    private static final String LIST = """
            SELECT e.enemy_character_id, c.name, e.kill_count, e.last_killed_at
              FROM pvp_enemy e JOIN character c ON c.id = e.enemy_character_id
             WHERE e.character_id = ?
             ORDER BY e.last_killed_at DESC, e.enemy_character_id DESC
            """;

    private static final String REMOVE = """
            DELETE FROM pvp_enemy e
             USING character c
             WHERE e.character_id = ? AND c.id = e.enemy_character_id AND c.name = ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresEnemyListAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void recordKill(long victimId, long killerId, java.time.Instant at, int maxEntries) {
        JdbcTransactionRunner.call(dataSource, connection -> {
            JdbcOperations.update(connection, UPSERT, victimId, killerId, Timestamp.from(at));
            return JdbcOperations.update(connection, TRIM, victimId, victimId, maxEntries);
        });
    }

    @Override
    public List<Enemy> list(long characterId) {
        return JdbcOperations.query(
                dataSource,
                LIST,
                rs -> new Enemy(
                        rs.getLong(1),
                        rs.getString(2),
                        rs.getInt(3),
                        rs.getTimestamp(4).toInstant()),
                characterId);
    }

    @Override
    public boolean remove(long characterId, String enemyName) {
        return JdbcOperations.update(dataSource, REMOVE, characterId, enemyName) > 0;
    }
}
