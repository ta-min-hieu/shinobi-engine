package dev.kage.server.database.character;

import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.ExpBuffPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ExpBuffPort} (ADR-164, V132) — đọc hạn x2 EXP. */
public final class PostgresExpBuffAdapter implements ExpBuffPort {

    private static final String EXPIRES_AT = "SELECT expires_at FROM character_exp_buff WHERE character_id = ?";

    private final DataSource dataSource;

    public PostgresExpBuffAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Instant> expiresAt(long characterId) {
        return JdbcOperations.queryOne(
                dataSource, EXPIRES_AT, rs -> rs.getTimestamp(1).toInstant(), characterId);
    }
}
