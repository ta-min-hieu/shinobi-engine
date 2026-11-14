package dev.kage.server.database.guild;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildDirectoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildDirectoryPort} (ADR-393) — {@code guild_member} JOIN {@code character}. */
public final class PostgresGuildDirectoryAdapter implements GuildDirectoryPort {

    private static final String SENIORITY = """
            SELECT c.name, m.joined_at FROM guild_member m JOIN character c ON c.id = m.character_id
             WHERE m.guild_id = ? ORDER BY m.joined_at, c.name
            """;

    private static final String SEARCH = """
            SELECT g.name, g.level, COALESCE(g.tag, '') AS tag, c.name AS leader_name,
                   (SELECT COUNT(*) FROM guild_member m WHERE m.guild_id = g.id) AS members
              FROM guild g JOIN character c ON c.id = g.leader_character_id
             WHERE g.name ILIKE ? ESCAPE '\\'
             ORDER BY g.level DESC, g.name LIMIT ?
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildDirectoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Found> search(String fragment, int limit) {
        return JdbcOperations.query(
                dataSource,
                SEARCH,
                rs -> new Found(
                        rs.getString("name"),
                        rs.getInt("level"),
                        rs.getInt("members"),
                        rs.getString("leader_name"),
                        rs.getString("tag")),
                "%" + escapeLike(fragment) + "%",
                limit);
    }

    /** Thoát {@code \\}, {@code %}, {@code _} để người chơi gõ gì cũng là chữ thường. Tĩnh để test. */
    static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    @Override
    public List<Senior> seniority(long guildId) {
        return JdbcOperations.query(
                dataSource,
                SENIORITY,
                rs -> new Senior(
                        rs.getString("name"), rs.getTimestamp("joined_at").toInstant()),
                guildId);
    }
}
