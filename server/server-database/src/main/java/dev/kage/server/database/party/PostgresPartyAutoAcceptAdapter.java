package dev.kage.server.database.party;

import javax.sql.DataSource;

import dev.kage.party.domain.PartyAutoAcceptPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link PartyAutoAcceptPort} (ADR-286, V171) — cột {@code character.auto_accept_friend_party}. */
public final class PostgresPartyAutoAcceptAdapter implements PartyAutoAcceptPort {

    private static final String GET = "SELECT auto_accept_friend_party FROM character WHERE id = ?";
    private static final String SET = "UPDATE character SET auto_accept_friend_party = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPartyAutoAcceptAdapter(DataSource dataSource) {
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
