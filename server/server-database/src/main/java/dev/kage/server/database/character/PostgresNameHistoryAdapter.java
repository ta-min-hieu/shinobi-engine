package dev.kage.server.database.character;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.character.domain.NameHistoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link NameHistoryPort} (ADR-343) — bảng {@code character_name_history} (V120, chỉ mục V178). */
public final class PostgresNameHistoryAdapter implements NameHistoryPort {

    private static final String HISTORY = """
            SELECT old_name, changed_at FROM character_name_history WHERE character_id = ? ORDER BY id DESC
            """;
    private static final String BY_OLD = """
            SELECT character_id FROM character_name_history WHERE lower(old_name) = lower(?) ORDER BY id DESC LIMIT 1
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresNameHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> history(long characterId) {
        return JdbcOperations.query(
                dataSource,
                HISTORY,
                rs -> new Entry(
                        rs.getString("old_name"), rs.getTimestamp("changed_at").toInstant()),
                characterId);
    }

    @Override
    public Optional<Long> characterWithOldName(String oldName) {
        return JdbcOperations.queryOne(dataSource, BY_OLD, rs -> rs.getLong(1), oldName);
    }
}
