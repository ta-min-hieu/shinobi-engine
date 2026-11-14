package dev.kage.server.database.guild;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildNameHistoryPort;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildNameHistoryPort} (ADR-423) — bảng {@code guild_name_history} (V184). */
public final class PostgresGuildNameHistoryAdapter implements GuildNameHistoryPort {

    private static final String INSERT =
            "INSERT INTO guild_name_history (guild_id, old_name, new_name, changed_at) VALUES (?, ?, ?, ?)";
    private static final String HISTORY =
            "SELECT old_name, new_name, changed_at FROM guild_name_history WHERE guild_id = ? ORDER BY changed_at";
    private static final String FIND = """
            SELECT id FROM guild WHERE lower(name) = lower(?)
            UNION ALL
            SELECT guild_id FROM (
                SELECT guild_id FROM guild_name_history WHERE lower(old_name) = lower(?)
                 ORDER BY changed_at DESC LIMIT 1) h
            LIMIT 1
            """;

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildNameHistoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void record(long guildId, String oldName, String newName, Instant at) {
        JdbcOperations.update(dataSource, INSERT, guildId, oldName, newName, Timestamp.from(at));
    }

    @Override
    public List<Rename> history(long guildId) {
        return JdbcOperations.query(
                dataSource,
                HISTORY,
                rs -> new Rename(
                        rs.getString("old_name"),
                        rs.getString("new_name"),
                        rs.getTimestamp("changed_at").toInstant()),
                guildId);
    }

    @Override
    public Optional<Long> findGuildId(String name) {
        return JdbcOperations.queryOne(dataSource, FIND, rs -> rs.getLong(1), name, name);
    }
}
