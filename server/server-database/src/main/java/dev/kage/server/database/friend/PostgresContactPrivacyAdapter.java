package dev.kage.server.database.friend;

import javax.sql.DataSource;

import dev.kage.friend.domain.ContactPrivacyPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ContactPrivacyPort} (ADR-236/237, V167) — hai cột trên {@code character}. */
public final class PostgresContactPrivacyAdapter implements ContactPrivacyPort {

    private static final String GET = "SELECT do_not_disturb, block_stranger_invites FROM character WHERE id = ?";
    private static final String SET_DND = "UPDATE character SET do_not_disturb = ? WHERE id = ?";
    private static final String SET_STRANGERS = "UPDATE character SET block_stranger_invites = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresContactPrivacyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Settings settings(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        GET,
                        rs -> new Settings(rs.getBoolean("do_not_disturb"), rs.getBoolean("block_stranger_invites")),
                        characterId)
                .orElse(Settings.NONE);
    }

    @Override
    public void setDoNotDisturb(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, SET_DND, enabled, characterId);
    }

    @Override
    public void setBlockStrangerInvites(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, SET_STRANGERS, enabled, characterId);
    }
}
