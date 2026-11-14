package dev.kage.server.database.character;

import javax.sql.DataSource;

import dev.kage.character.domain.PvpStatsPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link PvpStatsPort} (ADR-378) — cột {@code player_kills}/{@code deaths_by_player} (V181). */
public final class PostgresPvpStatsAdapter implements PvpStatsPort {

    private static final String ADD_KILL = """
            INSERT INTO character_lifetime_stats (character_id, player_kills) VALUES (?, 1)
            ON CONFLICT (character_id) DO UPDATE SET player_kills = character_lifetime_stats.player_kills + 1
            """;
    private static final String ADD_DEATH = """
            INSERT INTO character_lifetime_stats (character_id, deaths_by_player) VALUES (?, 1)
            ON CONFLICT (character_id) DO UPDATE SET deaths_by_player = character_lifetime_stats.deaths_by_player + 1
            """;
    private static final String READ =
            "SELECT player_kills, deaths_by_player FROM character_lifetime_stats WHERE character_id = ?";

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresPvpStatsAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void recordKill(long killerId, long victimId) {
        JdbcOperations.update(dataSource, ADD_KILL, killerId);
        JdbcOperations.update(dataSource, ADD_DEATH, victimId);
    }

    @Override
    public Stats stats(long characterId) {
        return JdbcOperations.queryOne(
                        dataSource,
                        READ,
                        rs -> new Stats(rs.getInt("player_kills"), rs.getInt("deaths_by_player")),
                        characterId)
                .orElse(new Stats(0, 0));
    }
}
