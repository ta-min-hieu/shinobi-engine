package dev.kage.server.database.mail;

import javax.sql.DataSource;

import dev.kage.mail.domain.StrangerMailPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link StrangerMailPort} (ADR-212, V163) — cột {@code character.block_stranger_mail}. */
public final class PostgresStrangerMailAdapter implements StrangerMailPort {

    private static final String GET = "SELECT block_stranger_mail FROM character WHERE id = ?";
    private static final String SET = "UPDATE character SET block_stranger_mail = ? WHERE id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresStrangerMailAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public boolean blocksStrangers(long characterId) {
        return JdbcOperations.queryOne(dataSource, GET, rs -> rs.getBoolean(1), characterId)
                .orElse(false);
    }

    @Override
    public void setBlocksStrangers(long characterId, boolean enabled) {
        JdbcOperations.update(dataSource, SET, enabled, characterId);
    }
}
