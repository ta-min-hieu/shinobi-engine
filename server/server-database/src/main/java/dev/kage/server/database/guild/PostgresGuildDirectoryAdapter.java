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

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildDirectoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
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
