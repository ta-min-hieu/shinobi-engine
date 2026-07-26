package dev.kage.server.database.guild;

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link GuildPort} trên PostgreSQL — bảng {@code guild}/{@code guild_member}/
 * {@code guild_join_request} (V14). {@link #createGuild} và {@link #approveJoinRequest} chạm 2
 * bảng nguyên tử bằng transaction thủ công trên 1 connection (KHÔNG {@code TransactionRunner}
 * riêng — xem javadoc {@link GuildPort}, cả 2 bảng đều thuộc feature này).
 *
 * <p>2 method đó (và các helper riêng của chúng) CỐ Ý không dùng {@link JdbcOperations} — chúng tự
 * quản lý commit/rollback/savepoint dựa vào {@link SQLException} checked bắt được tại đúng chỗ;
 * {@code JdbcOperations} bọc {@link SQLException} thành unchecked ngay bên trong, sẽ nhảy qua mất
 * khối {@code catch (SQLException e)} đang chờ rollback đúng chỗ. Phần đọc/ghi đơn giản còn lại
 * (không cần transaction thủ công) vẫn dùng {@code JdbcOperations} như các adapter khác.
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
        return JdbcOperations.queryOne(dataSource, FIND_BY_NAME, PostgresGuildAdapter::mapGuild, name);
    }

    @Override
    public Optional<Guild> findById(long guildId) {
        return JdbcOperations.queryOne(dataSource, FIND_BY_ID, PostgresGuildAdapter::mapGuild, guildId);
    }

    @Override
    public Optional<GuildMember> findMembershipOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND_MEMBERSHIP, PostgresGuildAdapter::mapMember, characterId);
    }

    @Override
    public List<GuildMember> listMembers(long guildId) {
        return JdbcOperations.query(dataSource, LIST_MEMBERS, PostgresGuildAdapter::mapMember, guildId);
    }

    // PMD AvoidCatchingGenericException: đúng ý — transaction PHẢI rollback khi action ném BẤT KỲ
    // RuntimeException nào (VD DuplicateGuildNameException từ insertGuild qua JdbcOperations).
    // Ranh giới transaction, không phải nơi xử lý lỗi nghiệp vụ cụ thể — rethrow ngay sau rollback,
    // cùng tinh thần JdbcTransactionRunner.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public Guild createGuild(String name, long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                long guildId = insertGuild(connection, name, leaderCharacterId, now);
                insertMember(connection, new NewMember(guildId, leaderCharacterId, GuildRole.LEADER, now));
                connection.commit();
                return new Guild(guildId, name, leaderCharacterId, now);
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi tạo bang: " + name, e);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tạo bang: " + name, e);
        }
    }

    private static long insertGuild(Connection connection, String name, long leaderCharacterId, Instant now) {
        return JdbcOperations.insertReturningIdOrThrow(
                connection,
                INSERT_GUILD,
                () -> new DuplicateGuildNameException(name),
                name,
                leaderCharacterId,
                Timestamp.from(now));
    }

    /** Gói tham số cho {@link #insertMember} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record NewMember(long guildId, long characterId, GuildRole role, Instant joinedAt) {
    }

    private static void insertMember(Connection connection, NewMember member) {
        JdbcOperations.update(
                connection,
                INSERT_MEMBER,
                member.guildId(),
                member.characterId(),
                member.role().name(),
                Timestamp.from(member.joinedAt()));
    }

    @Override
    public boolean disbandGuild(long leaderCharacterId) {
        return JdbcOperations.update(dataSource, DISBAND, leaderCharacterId) > 0;
    }

    @Override
    public boolean leaveGuild(long characterId) {
        return JdbcOperations.update(dataSource, LEAVE, characterId) > 0;
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        return JdbcOperations.update(dataSource, KICK, targetCharacterId, leaderCharacterId) > 0;
    }

    @Override
    public Optional<GuildJoinRequest> findPendingRequestOfCharacter(long characterId) {
        return JdbcOperations.queryOne(
                dataSource, FIND_PENDING_REQUEST_OF_CHARACTER, PostgresGuildAdapter::mapJoinRequest, characterId);
    }

    @Override
    public List<GuildJoinRequest> listPendingRequests(long guildId) {
        return JdbcOperations.query(dataSource, LIST_PENDING_REQUESTS, PostgresGuildAdapter::mapJoinRequest, guildId);
    }

    @Override
    public GuildJoinRequest sendJoinRequest(long guildId, long characterId) {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            long id = JdbcOperations.insertReturningIdOrThrow(
                    connection,
                    INSERT_JOIN_REQUEST,
                    () -> new DuplicateGuildJoinRequestException(guildId, characterId),
                    guildId,
                    characterId,
                    Timestamp.from(now));
            return new GuildJoinRequest(id, guildId, characterId, now);
        } catch (SQLException e) {
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

    // Raw JDBC CỐ Ý ở đây (không JdbcOperations.update dùng chung với insertMember): cần bắt
    // SQLException CHECKED ngay tại chỗ để rollback(savepoint) rồi TIẾP TỤC transaction (dọn rác +
    // trả TARGET_ALREADY_IN_GUILD) — JdbcOperations bọc thành unchecked sẽ mất đúng điểm bắt này.
    private static ApproveResult tryInsertMemberOrDropStaleRequest(
            Connection connection, long requestId, PendingApproval approval) throws SQLException {
        // SAVEPOINT bắt buộc: sau lỗi unique_violation, Postgres đánh dấu CẢ transaction là
        // "aborted" — DELETE dọn rác ngay sau đó (cùng transaction) sẽ bị Postgres từ chối với
        // "current transaction is aborted" nếu không rollback về savepoint trước (phát hiện thật
        // bằng Testcontainers, không phải đoán trước — xem GuildIntegrationTest
        // #shouldDropStaleRequest...).
        Savepoint savepoint = connection.setSavepoint();
        try (PreparedStatement statement = connection.prepareStatement(INSERT_MEMBER)) {
            statement.setLong(1, approval.guildId());
            statement.setLong(2, approval.characterId());
            statement.setString(3, GuildRole.MEMBER.name());
            statement.setTimestamp(4, Timestamp.from(Instant.now()));
            statement.executeUpdate();
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
        return JdbcOperations.update(dataSource, REJECT_JOIN_REQUEST, requestId, leaderCharacterId) > 0;
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
