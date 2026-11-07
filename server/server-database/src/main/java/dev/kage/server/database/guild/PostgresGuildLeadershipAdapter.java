package dev.kage.server.database.guild;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildLeadershipPort;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildLeadershipPort} (ADR-183, V144). */
public final class PostgresGuildLeadershipAdapter implements GuildLeadershipPort {

    private static final String MEMBERSHIP = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";
    private static final String LOCK_GUILD = "SELECT id FROM guild WHERE id = ? FOR UPDATE";
    private static final String SET_ROLE = "UPDATE guild_member SET role = ? WHERE guild_id = ? AND character_id = ?";
    private static final String SET_LEADER = "UPDATE guild SET leader_character_id = ? WHERE id = ?";

    /** Thành viên bang. */
    private record Membership(long guildId, String role) {}

    private final DataSource dataSource;

    public PostgresGuildLeadershipAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Outcome transfer(long leaderId, long targetId, Instant at) {
        return JdbcTransactionRunner.call(dataSource, connection -> transferIn(connection, leaderId, targetId, at));
    }

    private static Outcome transferIn(Connection connection, long leaderId, long targetId, Instant at) {
        Optional<Membership> leader = membership(connection, leaderId);
        if (leader.isEmpty()) {
            return Outcome.NOT_IN_GUILD;
        }
        long guildId = leader.get().guildId();
        JdbcOperations.queryOne(connection, LOCK_GUILD, rs -> rs.getLong(1), guildId);
        // Đọc lại SAU khi khoá: hai lệnh nhường chức đồng thời không được cùng thấy mình là bang chủ.
        leader = membership(connection, leaderId);
        if (leader.isEmpty()
                || leader.get().guildId() != guildId
                || !"LEADER".equals(leader.get().role())) {
            return Outcome.NOT_LEADER;
        }
        Optional<Membership> target = membership(connection, targetId);
        if (target.isEmpty() || target.get().guildId() != guildId) {
            return Outcome.TARGET_NOT_IN_GUILD;
        }
        JdbcOperations.update(connection, SET_ROLE, "MEMBER", guildId, leaderId);
        JdbcOperations.update(connection, SET_ROLE, "LEADER", guildId, targetId);
        JdbcOperations.update(connection, SET_LEADER, targetId, guildId);
        GuildLog.append(
                connection, new GuildLog.Entry(guildId, GuildLog.Kind.LEADER_CHANGED, leaderId, targetId, 0L, ""), at);
        return Outcome.OK;
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection, MEMBERSHIP, rs -> new Membership(rs.getLong(1), rs.getString(2)), characterId);
    }
}
