package dev.kage.server.database.quest;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.quest.domain.QuestRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link QuestRankingPort}. */
public final class PostgresQuestRankingAdapter implements QuestRankingPort {

    private static final String TOP = """
            SELECT c.name, COUNT(*) AS v FROM character_quest_progress q
              JOIN character c ON c.id = q.character_id
             WHERE q.status IN ('COMPLETED', 'CLAIMED')
             GROUP BY c.id, c.name ORDER BY v DESC, c.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresQuestRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Ranked> top(int limit) {
        return JdbcOperations.query(dataSource, TOP, rs -> new Ranked(rs.getString(1), rs.getLong(2)), limit);
    }
}
