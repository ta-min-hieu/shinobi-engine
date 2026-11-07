package dev.kage.server.database.guild;

import java.sql.Connection;
import java.time.Instant;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.guild.domain.GuildLeadershipPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/** Hiện thực {@link GuildLeadershipPort} (ADR-183, V144). */
public final class PostgresGuildLeadershipAdapter implements GuildLeadershipPort {

    private static final String MEMBERSHIP = "SELECT guild_id, role FROM guild_member WHERE character_id = ?";
    private static final String LOCK_GUILD = "SELECT id FROM guild WHERE id = ? FOR UPDATE";
    private static final String SET_ROLE = "UPDATE guild_member SET role = ? WHERE guild_id = ? AND character_id = ?";
    private static final String SET_LEADER = "UPDATE guild SET leader_character_id = ? WHERE id = ?";
    private static final String COUNT_VICES =
            "SELECT COUNT(*) FROM guild_member WHERE guild_id = ? AND role = 'VICE_LEADER'";

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

    @Override
    public ViceOutcome setViceLeader(long leaderId, long targetId, boolean appoint, Instant at) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> setViceIn(connection, new ViceRequest(leaderId, targetId, appoint, at)));
    }

    /** Một lệnh bổ nhiệm / bãi nhiệm. */
    private record ViceRequest(long leaderId, long targetId, boolean appoint, Instant at) {}

    /** Bang đã khoá của bang chủ, hoặc lý do từ chối ({@code guildId} = 0 khi từ chối). */
    private record LeaderLock(ViceOutcome refusal, long guildId) {}

    /** Khoá dòng bang của {@code leaderId} rồi đọc lại vai SAU khi khoá — chỉ bang chủ qua được. */
    private static LeaderLock lockAsLeader(Connection connection, long leaderId) {
        Optional<Membership> leader = membership(connection, leaderId);
        if (leader.isEmpty()) {
            return new LeaderLock(ViceOutcome.NOT_IN_GUILD, 0L);
        }
        long guildId = leader.get().guildId();
        JdbcOperations.queryOne(connection, LOCK_GUILD, rs -> rs.getLong(1), guildId);
        leader = membership(connection, leaderId);
        if (leader.isEmpty()
                || leader.get().guildId() != guildId
                || !"LEADER".equals(leader.get().role())) {
            return new LeaderLock(ViceOutcome.NOT_LEADER, 0L);
        }
        return new LeaderLock(ViceOutcome.OK, guildId);
    }

    private static ViceOutcome setViceIn(Connection connection, ViceRequest request) {
        LeaderLock lock = lockAsLeader(connection, request.leaderId());
        if (lock.refusal() != ViceOutcome.OK) {
            return lock.refusal();
        }
        long guildId = lock.guildId();
        Optional<Membership> target = membership(connection, request.targetId());
        if (target.isEmpty() || target.get().guildId() != guildId) {
            return ViceOutcome.TARGET_NOT_IN_GUILD;
        }
        if ("LEADER".equals(target.get().role())) { // tự bổ nhiệm / bãi nhiệm chính mình (bang chủ)
            return request.appoint() ? ViceOutcome.ALREADY_VICE : ViceOutcome.NOT_VICE;
        }
        boolean isVice = "VICE_LEADER".equals(target.get().role());
        ViceOutcome refusal = viceRefusal(connection, guildId, request.appoint(), isVice);
        if (refusal != ViceOutcome.OK) {
            return refusal;
        }
        JdbcOperations.update(
                connection, SET_ROLE, request.appoint() ? "VICE_LEADER" : "MEMBER", guildId, request.targetId());
        GuildLog.append(
                connection,
                new GuildLog.Entry(
                        guildId,
                        request.appoint() ? GuildLog.Kind.VICE_APPOINTED : GuildLog.Kind.VICE_DISMISSED,
                        request.leaderId(),
                        request.targetId(),
                        0L,
                        ""),
                request.at());
        return ViceOutcome.OK;
    }

    /** OK nếu được làm; ngược lại lý do từ chối. Gọi SAU khi đã khoá dòng bang (trần đếm an toàn). */
    private static ViceOutcome viceRefusal(Connection connection, long guildId, boolean appoint, boolean isVice) {
        if (appoint && isVice) {
            return ViceOutcome.ALREADY_VICE;
        }
        if (!appoint && !isVice) {
            return ViceOutcome.NOT_VICE;
        }
        if (appoint
                && JdbcOperations.queryOne(connection, COUNT_VICES, rs -> rs.getInt(1), guildId)
                                .orElse(0)
                        >= GuildRole.MAX_VICE_LEADERS) {
            return ViceOutcome.VICE_LIMIT_REACHED;
        }
        return ViceOutcome.OK;
    }

    private static Optional<Membership> membership(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                connection, MEMBERSHIP, rs -> new Membership(rs.getLong(1), rs.getString(2)), characterId);
    }
}
