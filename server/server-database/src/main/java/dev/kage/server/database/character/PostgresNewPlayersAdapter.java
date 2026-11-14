package dev.kage.server.database.character;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.NewPlayersPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link NewPlayersPort} (ADR-417) — bảng {@code character}. */
public final class PostgresNewPlayersAdapter implements NewPlayersPort {

    private static final String COUNT = "SELECT COUNT(*) FROM character WHERE created_at >= ?";
    private static final String NEWEST =
            "SELECT name FROM character WHERE created_at >= ? ORDER BY created_at DESC, id DESC LIMIT ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresNewPlayersAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public int countSince(Instant since) {
        return JdbcOperations.queryOne(dataSource, COUNT, rs -> rs.getInt(1), Timestamp.from(since))
                .orElse(0);
    }

    @Override
    public List<String> newestSince(Instant since, int limit) {
        return JdbcOperations.query(dataSource, NEWEST, rs -> rs.getString("name"), Timestamp.from(since), limit);
    }
}
