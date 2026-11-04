package dev.kage.server.database.character;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.GoldHistoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GoldHistoryPort} (ADR-169, V135) — dòng do {@code GoldLedger} ghi. */
public final class PostgresGoldHistoryAdapter implements GoldHistoryPort {

    private static final String RECENT = """
            SELECT delta, balance, reason_key, created_at FROM gold_ledger_entry
             WHERE character_id = ? ORDER BY id DESC LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresGoldHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getLong(1),
                        rs.getLong(2),
                        rs.getString(3),
                        rs.getTimestamp(4).toInstant()),
                characterId,
                limit);
    }
}
