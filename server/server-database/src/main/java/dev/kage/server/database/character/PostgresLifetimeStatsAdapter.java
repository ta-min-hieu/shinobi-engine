package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.LifetimeStatsPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link LifetimeStatsPort} (ADR-223, V165) — cộng dồn bằng một câu upsert, không đọc trước. */
public final class PostgresLifetimeStatsAdapter implements LifetimeStatsPort {

    private static final String ADD_DEATH = """
            INSERT INTO character_lifetime_stats (character_id, deaths) VALUES (?, 1)
            ON CONFLICT (character_id) DO UPDATE SET deaths = character_lifetime_stats.deaths + 1
            """;

    private static final String ADD_PLAYTIME = """
            INSERT INTO character_lifetime_stats (character_id, playtime_seconds) VALUES (?, ?)
            ON CONFLICT (character_id)
            DO UPDATE SET playtime_seconds = character_lifetime_stats.playtime_seconds + EXCLUDED.playtime_seconds
            """;

    private static final String SELECT =
            "SELECT deaths, playtime_seconds FROM character_lifetime_stats WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresLifetimeStatsAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void addDeath(long characterId) {
        JdbcOperations.update(dataSource, ADD_DEATH, characterId);
    }

    @Override
    public void addPlaytime(long characterId, long seconds) {
        JdbcOperations.update(dataSource, ADD_PLAYTIME, characterId, seconds);
    }

    @Override
    public Stats of(long characterId) {
        return JdbcOperations.queryOne(dataSource, SELECT, rs -> new Stats(rs.getInt(1), rs.getLong(2)), characterId)
                .orElse(Stats.NONE);
    }
}
