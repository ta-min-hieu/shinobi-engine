package dev.kage.server.database.character;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.character.domain.WealthRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link WealthRankingPort} (ADR-241) — đọc {@code character_stat.gold} giảm dần (chỉ mục V168). */
public final class PostgresWealthRankingAdapter implements WealthRankingPort {

    private static final String RICHEST =
            "SELECT character_id, gold FROM character_stat WHERE gold > 0 ORDER BY gold DESC, character_id LIMIT ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresWealthRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Map<Long, Long> richest(int limit) {
        Map<Long, Long> rows = new LinkedHashMap<>();
        for (Map.Entry<Long, Long> row : JdbcOperations.query(
                dataSource, RICHEST, rs -> Map.entry(rs.getLong("character_id"), rs.getLong("gold")), limit)) {
            rows.put(row.getKey(), row.getValue());
        }
        return rows;
    }
}
