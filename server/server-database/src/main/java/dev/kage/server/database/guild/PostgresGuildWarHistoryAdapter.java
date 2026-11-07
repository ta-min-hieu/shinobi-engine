package dev.kage.server.database.guild;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildWarHistoryPort;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link GuildWarHistoryPort} (ADR-208) — đọc thẳng {@code guild_war} đã chốt (V88), không bảng mới. Bang đối
 * địch đã giải tán thì trận bị CASCADE xoá theo (ADR-102), nên lịch sử chỉ còn trận với bang còn tồn tại.
 */
public final class PostgresGuildWarHistoryAdapter implements GuildWarHistoryPort {

    private static final String RECENT = """
            SELECT w.guild_a = m.guild_id AS ours_is_a, ga.name AS a_name, gb.name AS b_name,
                   w.kills_a, w.kills_b, w.winner_guild_id, m.guild_id AS our_guild, w.ends_at
              FROM guild_member m
              JOIN guild_war w ON (w.guild_a = m.guild_id OR w.guild_b = m.guild_id)
              JOIN guild ga ON ga.id = w.guild_a
              JOIN guild gb ON gb.id = w.guild_b
             WHERE m.character_id = ? AND w.settled_at IS NOT NULL
             ORDER BY w.ends_at DESC, w.id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildWarHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<PastWar> recentOf(long characterId, int limit) {
        return JdbcOperations.query(dataSource, RECENT, PostgresGuildWarHistoryAdapter::row, characterId, limit);
    }

    private static PastWar row(ResultSet rs) throws SQLException {
        boolean oursIsA = rs.getBoolean("ours_is_a");
        long winner = rs.getLong("winner_guild_id");
        Result result;
        if (rs.wasNull()) {
            result = Result.DRAW;
        } else {
            result = winner == rs.getLong("our_guild") ? Result.WIN : Result.LOSS;
        }
        return new PastWar(
                rs.getString(oursIsA ? "b_name" : "a_name"),
                oursIsA,
                rs.getInt(oursIsA ? "kills_a" : "kills_b"),
                rs.getInt(oursIsA ? "kills_b" : "kills_a"),
                result,
                rs.getTimestamp("ends_at").toInstant());
    }
}
