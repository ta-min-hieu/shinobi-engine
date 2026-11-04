package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.InspectPrivacyPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link InspectPrivacyPort} (ADR-171, V137). */
public final class PostgresInspectPrivacyAdapter implements InspectPrivacyPort {

    private static final String HIDDEN = "SELECT hidden FROM character_inspect_privacy WHERE character_id = ?";

    private static final String UPSERT = """
            INSERT INTO character_inspect_privacy (character_id, hidden, updated_at) VALUES (?, ?, now())
            ON CONFLICT (character_id) DO UPDATE SET hidden = EXCLUDED.hidden, updated_at = now()
            """;

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
}
