package dev.kage.server.database.character;

import java.time.LocalDate;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.PlayerLookupPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.RowMapper;

/**
 * Hiện thực {@link PlayerLookupPort} (ADR-351/352/353) — chỉ đọc {@code login_streak}, {@code referral}, {@code
 * character}.
 */
public final class PostgresPlayerLookupAdapter implements PlayerLookupPort {

    private static final String TOP_STREAKS = """
            SELECT c.name, s.streak AS value FROM login_streak s JOIN character c ON c.id = s.character_id
             WHERE s.last_day >= ? ORDER BY s.streak DESC, s.best DESC, c.name LIMIT ?
            """;
    private static final String TOP_REFERRERS = """
            SELECT c.name, COUNT(*)::int AS value FROM referral r JOIN character c ON c.id = r.referrer_id
             WHERE r.rewarded_at IS NOT NULL GROUP BY c.name ORDER BY value DESC, c.name LIMIT ?
            """;
    private static final String SEARCH = """
            SELECT id, name, level FROM character
             WHERE lower(name) LIKE '%' || ? || '%' ESCAPE '\\'
             ORDER BY (lower(name) LIKE ? || '%' ESCAPE '\\') DESC, length(name), name LIMIT ?
            """;
    private static final RowMapper<Ranked> RANKED = rs -> new Ranked(rs.getString("name"), rs.getInt("value"));

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPlayerLookupAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> topStreaks(LocalDate since, int limit) {
        return JdbcOperations.query(dataSource, TOP_STREAKS, RANKED, since, limit);
    }

    @Override
    public List<Ranked> topReferrers(int limit) {
        return JdbcOperations.query(dataSource, TOP_REFERRERS, RANKED, limit);
    }

    @Override
    public List<Hit> search(String fragment, int limit) {
        String escaped = escapeLike(fragment.toLowerCase(java.util.Locale.ROOT));
        return JdbcOperations.query(
                dataSource,
                SEARCH,
                rs -> new Hit(rs.getLong("id"), rs.getString("name"), rs.getInt("level")),
                escaped,
                escaped,
                limit);
    }

    /** Thoát {@code % _ \} để chuỗi người gõ khớp nguyên văn. Tĩnh để test. */
    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
