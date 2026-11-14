package dev.kage.server.database.friend;

import javax.sql.DataSource;

import dev.kage.friend.domain.InvisibleModePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link InvisibleModePort} (ADR-383) — bảng {@code invisible_mode} (V182). */
public final class PostgresInvisibleModeAdapter implements InvisibleModePort {

    private static final String FIND = "SELECT 1 FROM invisible_mode WHERE character_id = ?";
    private static final String ENABLE =
            "INSERT INTO invisible_mode (character_id) VALUES (?) ON CONFLICT (character_id) DO NOTHING";
    private static final String DISABLE = "DELETE FROM invisible_mode WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresInvisibleModeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean isInvisible(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND, rs -> Boolean.TRUE, characterId)
                .isPresent();
    }

    @Override
    public void setInvisible(long characterId, boolean invisible) {
        JdbcOperations.update(dataSource, invisible ? ENABLE : DISABLE, characterId);
    }
}
