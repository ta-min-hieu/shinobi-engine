package dev.kage.server.database.guild;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildEventPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildEventPort} (ADR-256, V170). */
public final class PostgresGuildEventAdapter implements GuildEventPort {

    private static final String MEMBER = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";
    private static final String LOCK_GUILD = "SELECT id FROM guild WHERE id = ? FOR UPDATE";
    private static final String COUNT_UPCOMING =
            "SELECT COUNT(*) FROM guild_event WHERE guild_id = ? AND starts_at > ?";
    private static final String INSERT = """
            INSERT INTO guild_event (guild_id, created_by, title, starts_at, created_at) VALUES (?, ?, ?, ?, ?)
            """;
    private static final String DELETE = "DELETE FROM guild_event WHERE id = ? AND guild_id = ?";
    private static final String UPCOMING = """
            SELECT e.id, e.title, e.starts_at
              FROM guild_event e JOIN guild_member gm ON gm.guild_id = e.guild_id
             WHERE gm.character_id = ? AND e.starts_at > ?
             ORDER BY e.starts_at, e.id
            """;
    private static final String DUE = """
            SELECT e.id, e.guild_id, g.leader_character_id, e.title, e.starts_at
              FROM guild_event e JOIN guild g ON g.id = e.guild_id
             WHERE NOT e.reminded AND e.starts_at > ? AND e.starts_at <= ?
             ORDER BY e.starts_at
            """;
    private static final String MARK = "UPDATE guild_event SET reminded = TRUE WHERE id = ?";

    /** Thành viên: bang + có phải sĩ quan. */
    private record Membership(long guildId, boolean officer) {}

    private final DataSource dataSource;

    /** Dựng ở composition root. */
    public PostgresGuildEventAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public CreateOutcome create(Draft draft, int maxUpcoming) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Optional<Membership> member = membership(connection, draft.characterId());
            if (member.isEmpty()) {
                return CreateOutcome.NOT_IN_GUILD;
            }
            if (!member.get().officer()) {
                return CreateOutcome.NOT_OFFICER;
            }
            long guildId = member.get().guildId();
            JdbcOperations.queryOne(connection, LOCK_GUILD, rs -> rs.getLong(1), guildId);
            Timestamp now = Timestamp.from(draft.now());
            long upcoming = JdbcOperations.queryOne(connection, COUNT_UPCOMING, rs -> rs.getLong(1), guildId, now)
                    .orElse(0L);
            if (upcoming >= maxUpcoming) {
                return CreateOutcome.FULL;
            }
            JdbcOperations.update(
                    connection,
                    INSERT,
                    guildId,
                    draft.characterId(),
                    draft.title(),
                    Timestamp.from(draft.startsAt()),
                    now);
            return CreateOutcome.OK;
        });
    }

    @Override
    public CancelOutcome cancel(long characterId, long eventId) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Optional<Membership> member = membership(connection, characterId);
            if (member.isEmpty()) {
                return CancelOutcome.NOT_FOUND;
            }
            if (!member.get().officer()) {
                return CancelOutcome.NOT_OFFICER;
            }
            return JdbcOperations.update(
                                    connection, DELETE, eventId, member.get().guildId())
                            > 0
                    ? CancelOutcome.OK
                    : CancelOutcome.NOT_FOUND;
        });
    }

    @Override
    public List<Event> upcoming(long characterId, Instant now) {
        return JdbcOperations.query(
                dataSource,
                UPCOMING,
                rs -> new Event(
                        rs.getLong("id"),
                        rs.getString("title"),
                        rs.getTimestamp("starts_at").toInstant()),
                characterId,
                Timestamp.from(now));
    }

    @Override
    public List<Due> due(Instant now, Instant until) {
        return JdbcOperations.query(
                dataSource,
                DUE,
                rs -> new Due(
                        rs.getLong("id"),
                        rs.getLong("guild_id"),
                        rs.getLong("leader_character_id"),
                        rs.getString("title"),
                        rs.getTimestamp("starts_at").toInstant()),
                Timestamp.from(now),
                Timestamp.from(until));
    }

    @Override
    public void markReminded(long eventId) {
        JdbcOperations.update(dataSource, MARK, eventId);
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection,
                MEMBER,
                rs -> new Membership(
                        rs.getLong("guild_id"),
                        "LEADER".equals(rs.getString("role")) || "VICE_LEADER".equals(rs.getString("role"))),
                characterId);
    }
}
