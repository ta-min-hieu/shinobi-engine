package dev.kage.server.database.character;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.character.domain.LifetimeRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link LifetimeRankingPort} (ADR-391/392) — {@code character_lifetime_stats} JOIN {@code character}. */
public final class PostgresLifetimeRankingAdapter implements LifetimeRankingPort {

    private static final String TOP_KILLS = """
            SELECT c.name, s.player_kills AS value FROM character_lifetime_stats s
              JOIN character c ON c.id = s.character_id
             WHERE s.player_kills > 0 ORDER BY s.player_kills DESC, c.name LIMIT ?
            """;
    private static final String TOP_PLAYTIME = """
            SELECT c.name, s.playtime_seconds AS value FROM character_lifetime_stats s
              JOIN character c ON c.id = s.character_id
             WHERE s.playtime_seconds > 0 ORDER BY s.playtime_seconds DESC, c.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLifetimeRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> topPlayerKills(int limit) {
        return JdbcOperations.query(dataSource, TOP_KILLS, PostgresLifetimeRankingAdapter::ranked, limit);
    }

    @Override
    public List<Ranked> topPlaytime(int limit) {
        return JdbcOperations.query(dataSource, TOP_PLAYTIME, PostgresLifetimeRankingAdapter::ranked, limit);
    }

    private static Ranked ranked(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new Ranked(rs.getString("name"), rs.getLong("value"));
    }
}
