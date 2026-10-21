package dev.kage.server.database.gm;

import java.sql.Timestamp;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.gm.domain.GameEvent;
import dev.kage.gm.domain.GameEventKind;
import dev.kage.gm.domain.GameEventPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GameEventPort} (ADR-067, V69). */
public final class PostgresGameEventAdapter implements GameEventPort {

    private static final String LOAD_ALL = "SELECT kind, multiplier, ends_at FROM game_event";

    private static final String UPSERT = """
            INSERT INTO game_event (kind, multiplier, ends_at, gm_audit_log_id) VALUES (?, ?, ?, ?)
            ON CONFLICT (kind) DO UPDATE
               SET multiplier = EXCLUDED.multiplier,
                   ends_at = EXCLUDED.ends_at,
                   gm_audit_log_id = EXCLUDED.gm_audit_log_id
            """;

    private static final String DELETE = "DELETE FROM game_event WHERE kind = ?";

    private final DataSource dataSource;

    public PostgresGameEventAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<GameEvent> loadAll() {
        return JdbcOperations.query(
                dataSource,
                LOAD_ALL,
                rs -> new GameEvent(
                        GameEventKind.valueOf(rs.getString("kind")),
                        rs.getInt("multiplier"),
                        rs.getTimestamp("ends_at").toInstant()));
    }

    @Override
    public void save(GameEvent event, long gmAuditLogId) {
        JdbcOperations.update(
                dataSource,
                UPSERT,
                event.kind().name(),
                event.multiplier(),
                Timestamp.from(event.endsAt()),
                gmAuditLogId);
    }

    @Override
    public void clear(GameEventKind kind) {
        JdbcOperations.update(dataSource, DELETE, kind.name());
    }
}
