package dev.kage.server.database.monster;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.monster.domain.BossKillRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link BossKillRankingPort}. */
public final class PostgresBossKillRankingAdapter implements BossKillRankingPort {

    private static final String TOP = """
            SELECT c.name, COUNT(*) AS v FROM audit_log a
              JOIN character c ON c.id = a.character_id
             WHERE a.action = 'BOSS_KILL'
             GROUP BY c.id, c.name ORDER BY v DESC, c.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresBossKillRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> top(int limit) {
        return JdbcOperations.query(dataSource, TOP, rs -> new Ranked(rs.getString(1), rs.getLong(2)), limit);
    }
}
