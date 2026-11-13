package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.InspectNoticePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link InspectNoticePort} (ADR-330) — bảng {@code inspect_notice_mute} (V176). */
public final class PostgresInspectNoticeAdapter implements InspectNoticePort {

    private static final String MUTED = "SELECT 1 FROM inspect_notice_mute WHERE character_id = ?";
    private static final String MUTE =
            "INSERT INTO inspect_notice_mute (character_id) VALUES (?) ON CONFLICT DO NOTHING";
    private static final String UNMUTE = "DELETE FROM inspect_notice_mute WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresInspectNoticeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean muted(long characterId) {
        return JdbcOperations.queryOne(dataSource, MUTED, rs -> rs.getInt(1), characterId)
                .isPresent();
    }

    @Override
    public void setMuted(long characterId, boolean muted) {
        JdbcOperations.update(dataSource, muted ? MUTE : UNMUTE, characterId);
    }
}
