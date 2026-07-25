package dev.kage.server.database.guild;

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link GuildPort} trên PostgreSQL — bảng {@code guild}/{@code guild_member}/
 * {@code guild_join_request} (V14). {@link #createGuild} và {@link #approveJoinRequest} chạm 2
 * bảng nguyên tử bằng transaction thủ công trên 1 connection (KHÔNG {@code TransactionRunner}
 * riêng — xem javadoc {@link GuildPort}, cả 2 bảng đều thuộc feature này).
 */
public final class PostgresGuildAdapter implements GuildPort {

    // Mã lỗi Postgres cho unique_violation (SQLState).
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private static final String FIND_BY_NAME = """
            SELECT id, name, leader_character_id, created_at FROM guild WHERE name = ?
            """;

    private static final String FIND_BY_ID = """
            SELECT id, name, leader_character_id, created_at FROM guild WHERE id = ?
            """;

    private static final String FIND_MEMBERSHIP = """
            SELECT guild_id, character_id, role, joined_at FROM guild_member WHERE character_id = ?
            """;

    private static final String LIST_MEMBERS = """
            SELECT guild_id, character_id, role, joined_at FROM guild_member WHERE guild_id = ?
            """;

    private static final String INSERT_GUILD = """
            INSERT INTO guild (name, leader_character_id, created_at) VALUES (?, ?, ?) RETURNING id
            """;

    private static final String INSERT_MEMBER = """
            INSERT INTO guild_member (guild_id, character_id, role, joined_at) VALUES (?, ?, ?, ?)
            """;

    private static final String DISBAND = """
            DELETE FROM guild WHERE leader_character_id = ?
            """;

    private static final String LEAVE = """
            DELETE FROM guild_member WHERE character_id = ? AND role = 'MEMBER'
            """;

    private static final String KICK = """
            DELETE FROM guild_member WHERE character_id = ? AND role = 'MEMBER'
              AND guild_id = (SELECT guild_id FROM guild_member WHERE character_id = ? AND role = 'LEADER')
            """;

    private static final String FIND_PENDING_REQUEST_OF_CHARACTER = """
            SELECT id, guild_id, character_id, requested_at FROM guild_join_request WHERE character_id = ?
            """;

    private static final String LIST_PENDING_REQUESTS = """
            SELECT id, guild_id, character_id, requested_at FROM guild_join_request WHERE guild_id = ?
            """;

    private static final String INSERT_JOIN_REQUEST = """
            INSERT INTO guild_join_request (guild_id, character_id, requested_at) VALUES (?, ?, ?) RETURNING id
            """;

    private static final String SELECT_JOIN_REQUEST_FOR_APPROVAL = """
            SELECT gjr.character_id, gjr.guild_id, g.leader_character_id
              FROM guild_join_request gjr JOIN guild g ON g.id = gjr.guild_id
             WHERE gjr.id = ?
             FOR UPDATE OF gjr
            """;

    private static final String DELETE_JOIN_REQUEST = """
            DELETE FROM guild_join_request WHERE id = ?
            """;

    private static final String REJECT_JOIN_REQUEST = """
            DELETE FROM guild_join_request gjr USING guild g
             WHERE gjr.id = ? AND gjr.guild_id = g.id AND g.leader_character_id = ?
            """;

    private final DataSource dataSource;

    public PostgresGuildAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Guild> findByName(String name) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_NAME)) {
            statement.setString(1, name);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra bang theo tên: " + name, e);
        }
    }

    @Override
    public Optional<Guild> findById(long guildId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, guildId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapGuild(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra bang theo id: " + guildId, e);
        }
    }

    @Override
    public Optional<GuildMember> findMembershipOf(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_MEMBERSHIP)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra thành viên bang: characterId=" + characterId, e);
        }
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST_MEMBERS)) {
            statement.setLong(1, guildId);
            ArrayList<GuildMember> result = new ArrayList<GuildMember>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMember(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê thành viên bang: guildId=" + guildId, e);
        }
    }

    @Override
    public Guild createGuild(String name, long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                long guildId;
                try (PreparedStatement insertGuild = connection.prepareStatement(INSERT_GUILD)) {
                    insertGuild.setString(1, name);
                    insertGuild.setLong(2, leaderCharacterId);
                    insertGuild.setTimestamp(3, Timestamp.from(now));
                    try (ResultSet rs = insertGuild.executeQuery()) {
                        rs.next();
                        guildId = rs.getLong(1);
                    }
                }
                try (PreparedStatement insertMember = connection.prepareStatement(INSERT_MEMBER)) {
                    insertMember.setLong(1, guildId);
                    insertMember.setLong(2, leaderCharacterId);
                    insertMember.setString(3, GuildRole.LEADER.name());
                    insertMember.setTimestamp(4, Timestamp.from(now));
                    insertMember.executeUpdate();
                }
                connection.commit();
                return new Guild(guildId, name, leaderCharacterId, now);
            } catch (SQLException e) {
                connection.rollback();
                if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                    throw new DuplicateGuildNameException(name);
                }
                throw new DatabaseAccessException("Lỗi tạo bang: " + name, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tạo bang: " + name, e);
        }
    }

    @Override
    public boolean disbandGuild(long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(DISBAND)) {
            statement.setLong(1, leaderCharacterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi giải tán bang: leaderCharacterId=" + leaderCharacterId, e);
        }
    }

    @Override
    public boolean leaveGuild(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LEAVE)) {
            statement.setLong(1, characterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi rời bang: characterId=" + characterId, e);
        }
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(KICK)) {
            statement.setLong(1, targetCharacterId);
            statement.setLong(2, leaderCharacterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi kick thành viên: leaderCharacterId=" + leaderCharacterId
                            + ", targetCharacterId=" + targetCharacterId, e);
        }
    }

    @Override
    public Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_PENDING_REQUEST_OF_CHARACTER)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapJoinRequest(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra đơn xin gia nhập: characterId=" + characterId, e);
        }
    }

    @Override
    public List<GuildJoinRequest> listPendingRequests(long guildId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(LIST_PENDING_REQUESTS)) {
            statement.setLong(1, guildId);
            ArrayList<GuildJoinRequest> result = new ArrayList<GuildJoinRequest>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapJoinRequest(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê đơn xin gia nhập: guildId=" + guildId, e);
        }
    }

    @Override
    public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_JOIN_REQUEST)) {
            statement.setLong(1, guildId);
            statement.setLong(2, characterId);
            Instant now = Instant.now();
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new GuildJoinRequest(rs.getLong(1), guildId, characterId, now);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new DuplicateGuildJoinRequestException(guildId, characterId);
            }
            throw new DatabaseAccessException(
                    "Lỗi gửi đơn xin gia nhập: guildId=" + guildId + ", characterId=" + characterId, e);
        }
    }

    @Override
    public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Optional<PendingApproval> approval = selectForApproval(connection, requestId);
                if (approval.isEmpty() || approval.get().guildLeaderCharacterId() != leaderCharacterId) {
                    connection.rollback();
                    return ApproveResult.NOT_FOUND;
                }
                ApproveResult result = tryInsertMemberOrDropStaleRequest(connection, requestId, approval.get());
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi duyệt đơn xin gia nhập: requestId=" + requestId, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi duyệt đơn xin gia nhập: requestId=" + requestId, e);
        }
    }

    private static Optional<PendingApproval> selectForApproval(Connection connection, long requestId)
            throws SQLException {
        try (PreparedStatement select = connection.prepareStatement(SELECT_JOIN_REQUEST_FOR_APPROVAL)) {
            select.setLong(1, requestId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new PendingApproval(
                        rs.getLong("character_id"), rs.getLong("guild_id"), rs.getLong("leader_character_id")));
            }
        }
    }

    private static ApproveResult tryInsertMemberOrDropStaleRequest(
            Connection connection, long requestId, PendingApproval approval) throws SQLException {
        // SAVEPOINT bắt buộc: sau lỗi unique_violation, Postgres đánh dấu CẢ transaction là
        // "aborted" — DELETE dọn rác ngay sau đó (cùng transaction) sẽ bị Postgres từ chối với
        // "current transaction is aborted" nếu không rollback về savepoint trước (phát hiện thật
        // bằng Testcontainers, không phải đoán trước — xem GuildIntegrationTest
        // #shouldDropStaleRequest...).
        Savepoint savepoint = connection.setSavepoint();
        try (PreparedStatement insertMember = connection.prepareStatement(INSERT_MEMBER)) {
            insertMember.setLong(1, approval.guildId());
            insertMember.setLong(2, approval.characterId());
            insertMember.setString(3, GuildRole.MEMBER.name());
            insertMember.setTimestamp(4, Timestamp.from(Instant.now()));
            insertMember.executeUpdate();
        } catch (SQLException e) {
            if (!UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw e;
            }
            // Người xin đã vào bang khác giữa lúc gửi đơn và lúc duyệt — đơn hết hạn,
            // dọn rác luôn.
            connection.rollback(savepoint);
            deleteJoinRequest(connection, requestId);
            return ApproveResult.TARGET_ALREADY_IN_GUILD;
        }
        deleteJoinRequest(connection, requestId);
        return ApproveResult.OK;
    }

    private static void deleteJoinRequest(Connection connection, long requestId) throws SQLException {
        try (PreparedStatement delete = connection.prepareStatement(DELETE_JOIN_REQUEST)) {
            delete.setLong(1, requestId);
            delete.executeUpdate();
        }
    }

    @Override
    public boolean rejectJoinRequest(long requestId, long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(REJECT_JOIN_REQUEST)) {
            statement.setLong(1, requestId);
            statement.setLong(2, leaderCharacterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi từ chối đơn xin gia nhập: requestId=" + requestId, e);
        }
    }

    private static Guild mapGuild(ResultSet rs) throws SQLException {
        return new Guild(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getLong("leader_character_id"),
                rs.getTimestamp("created_at").toInstant());
    }

    private static GuildMember mapMember(ResultSet rs) throws SQLException {
        return new GuildMember(
                rs.getLong("guild_id"),
                rs.getLong("character_id"),
                GuildRole.valueOf(rs.getString("role")),
                rs.getTimestamp("joined_at").toInstant());
    }

    private static GuildJoinRequest mapJoinRequest(ResultSet rs) throws SQLException {
        return new GuildJoinRequest(
                rs.getLong("id"), rs.getLong("guild_id"), rs.getLong("character_id"),
                rs.getTimestamp("requested_at").toInstant());
    }

    /** Kết quả SELECT ... FOR UPDATE trước khi duyệt — gói 3 cột thay vì truyền rời (PMD). */
    private record PendingApproval(long characterId, long guildId, long guildLeaderCharacterId) {
    }
}
