package dev.kage.server.database.checkin;

import java.sql.Date;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.sql.DataSource;

import dev.kage.checkin.domain.OnlineStatsPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link OnlineStatsPort} (ADR-431/432) — bảng {@code online_time} (V86). */
public final class PostgresOnlineStatsAdapter implements OnlineStatsPort {

    private static final String TOP = """
            SELECT o.character_id, c.name, o.seconds FROM online_time o JOIN character c ON c.id = o.character_id
             WHERE o.day = ? AND o.seconds > 0 ORDER BY o.seconds DESC, c.name LIMIT ?
            """;
    private static final String DAYS =
            "SELECT day, seconds FROM online_time WHERE character_id = ? AND day BETWEEN ? AND ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresOnlineStatsAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> topOfDay(LocalDate day, int limit) {
        return JdbcOperations.query(
                dataSource,
                TOP,
                rs -> new Ranked(rs.getLong("character_id"), rs.getString("name"), rs.getInt("seconds")),
                Date.valueOf(day),
                limit);
    }

    @Override
    public Map<LocalDate, Integer> daysOf(long characterId, LocalDate from, LocalDate to) {
        Map<LocalDate, Integer> result = new HashMap<>();
        JdbcOperations.query(
                        dataSource,
                        DAYS,
                        rs -> Map.entry(rs.getDate("day").toLocalDate(), rs.getInt("seconds")),
                        characterId,
                        Date.valueOf(from),
                        Date.valueOf(to))
                .forEach(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }
}
