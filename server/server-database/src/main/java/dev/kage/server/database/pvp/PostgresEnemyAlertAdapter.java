package dev.kage.server.database.pvp;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.pvp.domain.EnemyAlertPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link EnemyAlertPort} (ADR-426) — {@code pvp_enemy} + {@code enemy_alert_off} (V184). */
public final class PostgresEnemyAlertAdapter implements EnemyAlertPort {

    private static final String WATCHERS = """
            SELECT e.character_id FROM pvp_enemy e
             WHERE e.enemy_character_id = ?
               AND NOT EXISTS (SELECT 1 FROM enemy_alert_off o WHERE o.character_id = e.character_id)
            """;
    private static final String MUTED = "SELECT 1 FROM enemy_alert_off WHERE character_id = ?";
    private static final String MUTE =
            "INSERT INTO enemy_alert_off (character_id) VALUES (?) ON CONFLICT (character_id) DO NOTHING";
    private static final String UNMUTE = "DELETE FROM enemy_alert_off WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresEnemyAlertAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Long> watchersOf(long enemyId) {
        return JdbcOperations.query(dataSource, WATCHERS, rs -> rs.getLong(1), enemyId);
    }

    @Override
    public boolean muted(long characterId) {
        return JdbcOperations.queryOne(dataSource, MUTED, rs -> Boolean.TRUE, characterId)
                .isPresent();
    }

    @Override
    public void setMuted(long characterId, boolean muted) {
        JdbcOperations.update(dataSource, muted ? MUTE : UNMUTE, characterId);
    }
}
