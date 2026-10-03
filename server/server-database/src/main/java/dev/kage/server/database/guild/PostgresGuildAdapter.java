package dev.kage.server.database.guild;

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

import dev.kage.guild.domain.DuplicateGuildJoinRequestException;
import dev.kage.guild.domain.DuplicateGuildMembershipException;
import dev.kage.guild.domain.DuplicateGuildNameException;
import dev.kage.guild.domain.Guild;
import dev.kage.guild.domain.GuildDisbandedException;
import dev.kage.guild.domain.GuildJoinRequest;
import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link GuildPort} trên PostgreSQL — bảng {@code guild}/{@code guild_member}/
 * {@code guild_join_request} (V14). {@link #createGuild} và {@link #approveJoinRequest} chạm 2
 * bảng nguyên tử bằng transaction thủ công trên 1 connection (KHÔNG {@code TransactionRunner}
 * riêng của feature — xem javadoc {@link GuildPort}, cả 2 bảng đều thuộc feature này).
 *
 * <p>{@link #createGuild} dùng {@link JdbcTransactionRunner#call} (bản trả giá trị dùng chung —
 * đơn giản "commit khi xong, rollback+rethrow khi lỗi", không có nhánh rollback-mà-không-throw).
 * {@link #approveJoinRequest} PHẢI tự quản lý transaction thủ công riêng (không qua
 * {@code JdbcTransactionRunner}): có nhánh trả {@code NOT_FOUND} kèm rollback mà KHÔNG throw, và
 * {@link #tryInsertMemberOrDropStaleRequest} cần bắt {@link SQLException} checked giữa transaction
 * để rollback về savepoint rồi tiếp tục — 2 nhu cầu {@code JdbcTransactionRunner}/
 * {@link JdbcOperations} không phục vụ được.
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

    // KHÔNG khoá — chỉ để biết cần khoá dòng guild nào TRƯỚC khi khoá dòng đơn (xem
    // approveJoinRequest javadoc cho lý do thứ tự khoá).
    private static final String FIND_REQUEST_GUILD_ID = """
            SELECT guild_id FROM guild_join_request WHERE id = ?
            """;

    // FOR NO KEY UPDATE, không FOR UPDATE: xung đột với DELETE của disbandGuild (đúng thứ cần xếp
    // hàng) nhưng KHÔNG chặn FOR KEY SHARE mà lần kiểm FK của sendJoinRequest/INSERT guild_member
    // của giao dịch khác cần trên cùng dòng này.
    private static final String LOCK_GUILD = """
            SELECT id FROM guild WHERE id = ? FOR NO KEY UPDATE
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

    @Override
    public Guild createGuild(String name, long leaderCharacterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Instant now = Instant.now();
            long guildId = insertGuild(connection, name, leaderCharacterId, now);
            insertMember(connection, new NewMember(guildId, leaderCharacterId, GuildRole.LEADER, now));
            return new Guild(guildId, name, leaderCharacterId, now);
        });
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
    private record NewMember(long guildId, long characterId, GuildRole role, Instant joinedAt) {}

    // updateOrThrow (không phải update chung chung) — CreateGuildUseCase.findMembershipOf rồi mới
    // createGuild là check-then-act không atomic: 2 request "Tạo bang" (tên khác nhau) của CÙNG 1
    // nhân vật gần như đồng thời đều có thể vượt qua check đó trước khi 1 trong 2 transaction
    // commit. Không dịch unique_violation ở đây thì bên thua ném DatabaseAccessException chung
    // chung xuyên thẳng lên GatewayChannelHandler (không có try/catch) — client treo vô thời hạn
    // thay vì nhận CREATE_GUILD_OUTCOME_ALREADY_IN_GUILD (phát hiện thật bằng Testcontainers +
    // CyclicBarrier, xem GuildIntegrationTest#shouldTranslateRaceIntoDuplicateGuildMembershipException...).
    private static void insertMember(Connection connection, NewMember member) {
        JdbcOperations.updateOrThrow(
                connection,
                INSERT_MEMBER,
                () -> new DuplicateGuildMembershipException(member.characterId()),
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

    /**
     * {@inheritDoc}
     *
     * <p>Vi phạm FOREIGN KEY ở đây chỉ có thể là {@code guild_id} (người xin là nhân vật đang đăng
     * nhập, nhân vật không bao giờ bị xoá): leader giải tán bang đúng giữa lúc use case tra bang theo
     * tên và lúc ghi đơn (sửa 2026-10-03 — trước đó thành 500 {@code error.server_internal}).
     */
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
        } catch (DatabaseAccessException e) {
            if (JdbcOperations.isForeignKeyViolation(e)) {
                throw new GuildDisbandedException(guildId);
            }
            throw e;
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi gửi đơn xin gia nhập: guildId=" + guildId + ", characterId=" + characterId, e);
        }
    }

    /**
     * {@inheritDoc}
     *
     * <p>⚠️ Deadlock THẬT tìm được 2026-09-25 (xem {@code GuildConcurrencyIntegrationTest}): bản cũ
     * khoá dòng {@code guild_join_request} (FOR UPDATE) RỒI MỚI chạm dòng {@code guild} — qua lần
     * kiểm FK của INSERT {@code guild_member}. {@link #disbandGuild} làm NGƯỢC lại: DELETE dòng
     * {@code guild} trước, rồi CASCADE sang dòng đơn đang bị khoá. Leader bấm "Giải tán" đúng lúc
     * một lần "Duyệt" đang chạy ⇒ Postgres huỷ ngẫu nhiên một bên bằng "deadlock detected" — cùng họ
     * lỗi thứ tự khoá của {@code PostgresPartyAdapter#acceptInvite} (2026-09-23).
     *
     * <p>Vá bằng cách khoá dòng {@code guild} TRƯỚC (đọc {@code guild_id} không khoá để biết khoá dòng
     * nào), cùng thứ tự {@link #disbandGuild}. Bang đã bị giải tán thì trả {@code NOT_FOUND} — CASCADE
     * đã xoá đơn.
     */
    @Override
    public ApproveResult approveJoinRequest(long requestId, long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                if (!lockGuildOfRequest(connection, requestId)) {
                    connection.rollback();
                    return ApproveResult.NOT_FOUND;
                }
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

    /** {@code true} nếu đơn và bang của nó còn tồn tại — dòng {@code guild} đã bị khoá khi trả {@code true}. */
    private static boolean lockGuildOfRequest(Connection connection, long requestId) throws SQLException {
        long guildId;
        try (PreparedStatement find = connection.prepareStatement(FIND_REQUEST_GUILD_ID)) {
            find.setLong(1, requestId);
            try (ResultSet rs = find.executeQuery()) {
                if (!rs.next()) {
                    return false;
                }
                guildId = rs.getLong("guild_id");
            }
        }
        try (PreparedStatement lock = connection.prepareStatement(LOCK_GUILD)) {
            lock.setLong(1, guildId);
            try (ResultSet rs = lock.executeQuery()) {
                return rs.next();
            }
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
                rs.getLong("id"),
                rs.getLong("guild_id"),
                rs.getLong("character_id"),
                rs.getTimestamp("requested_at").toInstant());
    }

    /** Kết quả SELECT ... FOR UPDATE trước khi duyệt — gói 3 cột thay vì truyền rời (PMD). */
    private record PendingApproval(long characterId, long guildId, long guildLeaderCharacterId) {}
}
