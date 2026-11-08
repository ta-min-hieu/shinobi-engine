package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.InspectPrivacyPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link InspectPrivacyPort} (ADR-171, V137; dòng giới thiệu ADR-218, V164). */
public final class PostgresInspectPrivacyAdapter implements InspectPrivacyPort {

    private static final String HIDDEN = "SELECT hidden FROM character_inspect_privacy WHERE character_id = ?";

    private static final String UPSERT = """
            INSERT INTO character_inspect_privacy (character_id, hidden, updated_at) VALUES (?, ?, now())
            ON CONFLICT (character_id) DO UPDATE SET hidden = EXCLUDED.hidden, updated_at = now()
            """;

    private static final String BIO = "SELECT bio FROM character WHERE id = ?";
    private static final String SET_BIO = "UPDATE character SET bio = ? WHERE id = ?";

    private final DataSource dataSource;

    public PostgresInspectPrivacyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean hidden(long characterId) {
        return JdbcOperations.queryOne(dataSource, HIDDEN, rs -> rs.getBoolean(1), characterId)
                .orElse(false);
    }

    @Override
    public void setHidden(long characterId, boolean hidden) {
        JdbcOperations.update(dataSource, UPSERT, characterId, hidden);
    }

    @Override
    public String bio(long characterId) {
        return JdbcOperations.queryOne(dataSource, BIO, rs -> rs.getString(1), characterId)
                .orElse("");
    }

    @Override
    public void setBio(long characterId, String bio) {
        JdbcOperations.update(dataSource, SET_BIO, bio, characterId);
    }
}
