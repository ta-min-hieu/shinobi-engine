package dev.kage.server.database.guild;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildRecruitmentPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.JdbcOperations;

/** Hiện thực {@link GuildRecruitmentPort} (ADR-185, V146). */
public final class PostgresGuildRecruitmentAdapter implements GuildRecruitmentPort {

    private static final String MEMBERSHIP = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";

    private static final String UPSERT = """
            INSERT INTO guild_recruitment (guild_id, min_level, message, updated_at) VALUES (?, ?, ?, ?)
            ON CONFLICT (guild_id) DO UPDATE
               SET min_level = EXCLUDED.min_level, message = EXCLUDED.message, updated_at = EXCLUDED.updated_at
            """;

    private static final String DELETE = "DELETE FROM guild_recruitment WHERE guild_id = ?";

    private static final String LIST = """
            SELECT g.name, g.level, (SELECT COUNT(*) FROM guild_member m WHERE m.guild_id = g.id),
                   r.min_level, r.message, r.updated_at
              FROM guild_recruitment r JOIN guild g ON g.id = r.guild_id
             ORDER BY g.level DESC, r.updated_at DESC
             LIMIT ?
            """;

    private static final String MIN_LEVEL = """
            SELECT r.min_level FROM guild_recruitment r JOIN guild g ON g.id = r.guild_id WHERE g.name = ?
            """;

    /** Thành viên bang. */
    private record Membership(long guildId, String role) {}

    private final DataSource dataSource;

    public PostgresGuildRecruitmentAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Outcome set(long characterId, int minLevel, String message, Instant at) {
        Optional<Membership> membership = JdbcOperations.queryOne(
                dataSource, MEMBERSHIP, rs -> new Membership(rs.getLong(1), rs.getString(2)), characterId);
        if (membership.isEmpty()) {
            return Outcome.NOT_IN_GUILD;
        }
        if (!GuildRole.valueOf(membership.get().role()).canManageMembership()) {
            return Outcome.NOT_ALLOWED;
        }
        long guildId = membership.get().guildId();
        if (message == null) {
            JdbcOperations.update(dataSource, DELETE, guildId);
        } else {
            JdbcOperations.update(dataSource, UPSERT, guildId, minLevel, message, Timestamp.from(at));
        }
        return Outcome.OK;
    }

    @Override
    public List<Listing> list(int limit) {
        return JdbcOperations.query(
                dataSource,
                LIST,
                rs -> new Listing(
                        rs.getString(1),
                        rs.getInt(2),
                        rs.getInt(3),
                        rs.getInt(4),
                        rs.getString(5),
                        rs.getTimestamp(6).toInstant()),
                limit);
    }

    @Override
    public OptionalInt minLevelOf(String guildName) {
        return JdbcOperations.queryOne(dataSource, MIN_LEVEL, rs -> rs.getInt(1), guildName)
                .map(OptionalInt::of)
                .orElseGet(OptionalInt::empty);
    }
}
