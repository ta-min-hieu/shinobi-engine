package dev.kage.server.database.guild;

import java.util.List;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildLogPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực đọc của {@link GuildLogPort} (ADR-180, V143); ghi thì qua {@link GuildLog}. */
public final class PostgresGuildLogAdapter implements GuildLogPort {

    private static final String RECENT = """
            SELECT l.kind, COALESCE(a.name, ''), COALESCE(s.name, ''), l.amount, l.detail, l.created_at
              FROM guild_log l
              LEFT JOIN character a ON a.id = l.actor_id
              LEFT JOIN character s ON s.id = l.subject_id
             WHERE l.guild_id = (SELECT guild_id FROM guild_member WHERE character_id = ?)
             ORDER BY l.created_at DESC, l.id DESC
             LIMIT ?
            """;

    private final DataSource dataSource;

    public PostgresGuildLogAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public List<Entry> recent(long characterId, int limit) {
        return JdbcOperations.query(
                dataSource,
                RECENT,
                rs -> new Entry(
                        rs.getString(1),
                        rs.getString(2),
                        rs.getString(3),
                        rs.getLong(4),
                        rs.getString(5),
                        rs.getTimestamp(6).toInstant()),
                characterId,
                limit);
    }
}
