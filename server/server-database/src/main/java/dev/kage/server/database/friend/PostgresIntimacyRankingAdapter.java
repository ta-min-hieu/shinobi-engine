package dev.kage.server.database.friend;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.friend.domain.IntimacyRankingPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link IntimacyRankingPort} (ADR-434) — {@code friendship} JOIN {@code character} hai lần. */
public final class PostgresIntimacyRankingAdapter implements IntimacyRankingPort {

    private static final String TOP = """
            SELECT a.name AS first_name, b.name AS second_name, f.intimacy FROM friendship f
              JOIN character a ON a.id = f.requester_id
              JOIN character b ON b.id = f.addressee_id
             WHERE f.status = 'ACCEPTED' AND f.intimacy > 0
             ORDER BY f.intimacy DESC, a.name, b.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresIntimacyRankingAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Pair> top(int limit) {
        return JdbcOperations.query(
                dataSource,
                TOP,
                rs -> new Pair(rs.getString("first_name"), rs.getString("second_name"), rs.getInt("intimacy")),
                limit);
    }
}
