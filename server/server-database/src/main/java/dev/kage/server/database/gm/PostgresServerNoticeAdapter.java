package dev.kage.server.database.gm;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.gm.domain.ServerNoticePort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link ServerNoticePort} (ADR-103, V89) — đúng một dòng {@code id = 1}. */
public final class PostgresServerNoticeAdapter implements ServerNoticePort {

    private static final String CURRENT = "SELECT body FROM server_notice WHERE id = 1";

    private static final String UPSERT = """
            INSERT INTO server_notice (id, body, updated_at, updated_by) VALUES (1, ?, ?, ?)
            ON CONFLICT (id) DO UPDATE
               SET body = EXCLUDED.body, updated_at = EXCLUDED.updated_at, updated_by = EXCLUDED.updated_by
            """;

    private static final String CLEAR = "DELETE FROM server_notice WHERE id = 1";

    private final DataSource dataSource;

    public PostgresServerNoticeAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<String> current() {
        return JdbcOperations.queryOne(dataSource, CURRENT, rs -> rs.getString(1));
    }

    @Override
    public void set(String body, long updatedBy, Instant at) {
        JdbcOperations.update(dataSource, UPSERT, body, Timestamp.from(at), updatedBy);
    }

    @Override
    public void clear() {
        JdbcOperations.update(dataSource, CLEAR);
    }
}
