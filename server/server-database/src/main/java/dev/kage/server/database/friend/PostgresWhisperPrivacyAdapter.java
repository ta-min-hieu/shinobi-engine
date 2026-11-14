package dev.kage.server.database.friend;

import javax.sql.DataSource;

import dev.kage.friend.domain.WhisperPrivacyPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link WhisperPrivacyPort} (ADR-402) — bảng {@code whisper_friends_only} (V183). */
public final class PostgresWhisperPrivacyAdapter implements WhisperPrivacyPort {

    private static final String FIND = "SELECT 1 FROM whisper_friends_only WHERE character_id = ?";
    private static final String ENABLE =
            "INSERT INTO whisper_friends_only (character_id) VALUES (?) ON CONFLICT (character_id) DO NOTHING";
    private static final String DISABLE = "DELETE FROM whisper_friends_only WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresWhisperPrivacyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean friendsOnly(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND, rs -> Boolean.TRUE, characterId)
                .isPresent();
    }

    @Override
    public void setFriendsOnly(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, enabled ? ENABLE : DISABLE, characterId);
    }
}
