package dev.kage.server.database.achievement;

import javax.sql.DataSource;

import dev.kage.achievement.domain.AchievementSharePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link AchievementSharePort} (ADR-230, V166) — cột {@code character.share_achievements}. */
public final class PostgresAchievementShareAdapter implements AchievementSharePort {

    private static final String GET = "SELECT share_achievements FROM character WHERE id = ?";
    private static final String SET = "UPDATE character SET share_achievements = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresAchievementShareAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean enabled(long characterId) {
        return JdbcOperations.queryOne(dataSource, GET, rs -> rs.getBoolean(1), characterId)
                .orElse(false);
    }

    @Override
    public void setEnabled(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, SET, enabled, characterId);
    }
}
