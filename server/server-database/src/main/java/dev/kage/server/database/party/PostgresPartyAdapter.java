package dev.kage.server.database.party;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.DuplicatePartyMembershipException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link PartyPort} trên PostgreSQL — bảng {@code party}/{@code party_member}/
 * {@code party_invite} (V15). {@link #createParty}, {@link #acceptInvite}, {@link #leaveParty}
 * chạm nhiều bảng nguyên tử — cả 3 đều "commit khi xong / rollback+rethrow khi lỗi" thuần tuý
 * (không có nhánh rollback-mà-không-throw) nên dùng chung {@link JdbcTransactionRunner#call}
 * thay vì tự lặp lại khung transaction thủ công.
 */
public final class PostgresPartyAdapter implements PartyPort {

    private static final String FIND_MEMBERSHIP = """
            SELECT party_id, character_id, role, joined_at FROM party_member WHERE character_id = ?
            """;

    private static final String LIST_MEMBERS = """
            SELECT party_id, character_id, role, joined_at FROM party_member WHERE party_id = ?
            """;

    private static final String INSERT_PARTY = """
            INSERT INTO party (leader_character_id, created_at) VALUES (?, ?) RETURNING id
            """;

    // ON CONFLICT (character_id) DO NOTHING — xem insertMember: nhân vật vào 2 party cùng lúc thì
    // bên sau CHỜ bên trước commit rồi nhận 0 dòng, thay vì unique_violation làm hỏng transaction.
    private static final String INSERT_MEMBER = """
            INSERT INTO party_member (party_id, character_id, role, joined_at) VALUES (?, ?, ?, ?)
             ON CONFLICT (character_id) DO NOTHING
            """;

    private static final String FIND_PENDING_INVITE_OF_INVITEE = """
            SELECT id, party_id, invitee_character_id, invited_at FROM party_invite WHERE invitee_character_id = ?
            """;

    private static final String LIST_PENDING_INVITES_OF_INVITEE = """
            SELECT id, party_id, invitee_character_id, invited_at FROM party_invite WHERE invitee_character_id = ?
            """;

    private static final String INSERT_INVITE = """
            INSERT INTO party_invite (party_id, invitee_character_id, invited_at) VALUES (?, ?, ?) RETURNING id
            """;

    // KHÔNG khoá — chỉ để biết cần khoá dòng party nào TRƯỚC khi khoá chính dòng invite này (xem
    // SELECT_INVITE_FOR_ACCEPT javadoc trong acceptInviteInTransaction cho lý do thứ tự khoá).
    private static final String FIND_INVITE_PARTY_ID = """
            SELECT party_id FROM party_invite WHERE id = ? AND invitee_character_id = ?
            """;

    private static final String SELECT_INVITE_FOR_ACCEPT = """
            SELECT id, party_id, invitee_character_id FROM party_invite WHERE id = ? AND invitee_character_id = ?
             FOR UPDATE
            """;

    private static final String LOCK_PARTY = """
            SELECT id FROM party WHERE id = ? FOR UPDATE
            """;

    private static final String COUNT_MEMBERS = """
            SELECT COUNT(*) FROM party_member WHERE party_id = ?
            """;

    private static final String DELETE_INVITE = """
            DELETE FROM party_invite WHERE id = ?
            """;

    private static final String REJECT_INVITE = """
            DELETE FROM party_invite WHERE id = ? AND invitee_character_id = ?
            """;

    private static final String SELECT_MEMBERSHIP_FOR_UPDATE = """
            SELECT party_id, role FROM party_member WHERE character_id = ? FOR UPDATE
            """;

    private static final String DELETE_MEMBER = """
            DELETE FROM party_member WHERE party_id = ? AND character_id = ?
            """;

    private static final String FIND_EARLIEST_REMAINING_MEMBER = """
            SELECT character_id FROM party_member WHERE party_id = ? ORDER BY joined_at ASC LIMIT 1 FOR UPDATE
            """;

    private static final String PROMOTE_TO_LEADER = """
            UPDATE party_member SET role = 'LEADER' WHERE party_id = ? AND character_id = ?
            """;

    private static final String UPDATE_PARTY_LEADER = """
            UPDATE party SET leader_character_id = ? WHERE id = ?
            """;

    private static final String DELETE_PARTY = """
            DELETE FROM party WHERE id = ?
            """;

    private static final String KICK = """
            DELETE FROM party_member WHERE character_id = ? AND role = 'MEMBER'
              AND party_id = (SELECT party_id FROM party_member WHERE character_id = ? AND role = 'LEADER')
            """;

    private final DataSource dataSource;

    public PostgresPartyAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<PartyMember> findMembershipOf(long characterId) {
        return JdbcOperations.queryOne(dataSource, FIND_MEMBERSHIP, PostgresPartyAdapter::mapMember, characterId);
    }

    @Override
    public List<PartyMember> listMembers(long partyId) {
        return JdbcOperations.query(dataSource, LIST_MEMBERS, PostgresPartyAdapter::mapMember, partyId);
    }

    @Override
    public Party createParty(long leaderCharacterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> {
            Instant now = Instant.now();
            long partyId =
                    JdbcOperations.insertReturningId(connection, INSERT_PARTY, leaderCharacterId, Timestamp.from(now));
            if (!insertMember(connection, new NewMember(partyId, leaderCharacterId, PartyRole.LEADER, now))) {
                // Ném để JdbcTransactionRunner rollback CẢ dòng party vừa insert — không để party mồ côi.
                throw new DuplicatePartyMembershipException(leaderCharacterId);
            }
            return new Party(partyId, leaderCharacterId, now);
        });
    }

    @Override
    public Optional<PartyInvite> findPendingInviteOfInvitee(long characterId) {
        return JdbcOperations.queryOne(
                dataSource, FIND_PENDING_INVITE_OF_INVITEE, PostgresPartyAdapter::mapInvite, characterId);
    }

    @Override
    public List<PartyInvite> listPendingInvitesOfInvitee(long characterId) {
        return JdbcOperations.query(
                dataSource, LIST_PENDING_INVITES_OF_INVITEE, PostgresPartyAdapter::mapInvite, characterId);
    }

    @Override
    public PartyInvite invite(long partyId, long inviteeCharacterId) {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection()) {
            long id = JdbcOperations.insertReturningIdOrThrow(
                    connection,
                    INSERT_INVITE,
                    () -> new DuplicatePartyInviteException(partyId, inviteeCharacterId),
                    partyId,
                    inviteeCharacterId,
                    Timestamp.from(now));
            return new PartyInvite(id, partyId, inviteeCharacterId, now);
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi mời party: partyId=" + partyId + ", inviteeCharacterId=" + inviteeCharacterId, e);
        }
    }

    @Override
    public AcceptResult acceptInvite(long inviteId, long characterId) {
        return JdbcTransactionRunner.call(
                dataSource, connection -> acceptInviteInTransaction(connection, inviteId, characterId));
    }

    /**
     * ⚠️ Race THẬT tìm được 2026-09-23 (xem {@code PartyIntegrationTest
     * #shouldNotThrow_whenInviteeAcceptsWhileSoleLeaderConcurrentlyDissolvesTheParty}): bản cũ khoá
     * dòng {@code party_invite} (qua {@code SELECT_INVITE_FOR_ACCEPT}) RỒI MỚI khoá dòng {@code
     * party}. {@code leaveParty} (khi leader là người cuối, giải tán party) khoá dòng {@code party}
     * TRƯỚC rồi mới CASCADE xoá {@code party_invite} — NGƯỢC thứ tự. Nếu 1 lời mời đang PENDING đúng
     * lúc leader rời, 2 giao dịch khoá CHÉO nhau → Postgres phát hiện DEADLOCK THẬT và huỷ NGẪU
     * NHIÊN 1 trong 2 phía bằng lỗi không được xử lý (client bấm "Chấp nhận" hoặc "Rời nhóm" có thể
     * treo vô thời hạn — {@code GatewayChannelHandler} không có try/catch quanh use case này).
     *
     * <p>Vá bằng cách khoá {@code party} TRƯỚC {@code party_invite} ở CẢ HAI luồng — đọc {@code
     * party_id} không khoá để biết cần khoá dòng {@code party} nào, khoá dòng đó (còn tồn tại thì
     * mới an toàn tiếp tục; party đã bị giải tán thì trả thẳng {@code NOT_FOUND}, KHÔNG cần tự xoá
     * lại dòng {@code party_invite} vì CASCADE của {@code leaveParty} đã lo), rồi mới khoá lại đúng
     * dòng invite để đọc/xoá — cùng thứ tự {@code leaveParty} đã dùng, không còn đường nào 2 giao
     * dịch khoá chéo nhau.
     */
    private static AcceptResult acceptInviteInTransaction(Connection connection, long inviteId, long characterId) {
        Optional<Long> unlockedPartyId = JdbcOperations.queryOne(
                connection, FIND_INVITE_PARTY_ID, rs -> rs.getLong("party_id"), inviteId, characterId);
        if (unlockedPartyId.isEmpty()) {
            return AcceptResult.NOT_FOUND;
        }
        if (!lockPartyIfExists(connection, unlockedPartyId.get())) {
            // Party đã bị giải tán (leader là người cuối, vừa rời) đúng lúc lời mời này còn PENDING —
            // CASCADE của leaveParty đã hoặc sắp xoá dòng invite này, không cần tự xoá lại.
            return AcceptResult.NOT_FOUND;
        }
        Optional<Long> partyId = JdbcOperations.queryOne(
                connection, SELECT_INVITE_FOR_ACCEPT, rs -> rs.getLong("party_id"), inviteId, characterId);
        if (partyId.isEmpty()) {
            return AcceptResult.NOT_FOUND;
        }
        if (isAlreadyMember(connection, characterId)) {
            deleteInvite(connection, inviteId);
            return AcceptResult.ALREADY_IN_PARTY;
        }
        if (countMembers(connection, partyId.get()) >= Party.MAX_MEMBERS) {
            deleteInvite(connection, inviteId);
            return AcceptResult.PARTY_FULL;
        }
        boolean joined =
                insertMember(connection, new NewMember(partyId.get(), characterId, PartyRole.MEMBER, Instant.now()));
        deleteInvite(connection, inviteId);
        // isAlreadyMember ở trên không thấy transaction CHƯA commit của party khác — invitee vừa vào
        // party đó đồng thời (chấp nhận lời mời khác, hoặc tự tạo party). Lời mời này hết hạn như
        // nhánh ALREADY_IN_PARTY thường: đã xoá ở trên, không treo lại.
        return joined ? AcceptResult.OK : AcceptResult.ALREADY_IN_PARTY;
    }

    /** {@code true} nếu dòng {@code party} còn tồn tại — đã khoá FOR UPDATE khi trả {@code true}. */
    private static boolean lockPartyIfExists(Connection connection, long partyId) {
        return JdbcOperations.queryOne(connection, LOCK_PARTY, rs -> Boolean.TRUE, partyId)
                .isPresent();
    }

    private static boolean isAlreadyMember(Connection connection, long characterId) {
        return JdbcOperations.queryOne(connection, FIND_MEMBERSHIP, rs -> Boolean.TRUE, characterId)
                .isPresent();
    }

    private static int countMembers(Connection connection, long partyId) {
        return JdbcOperations.queryOne(connection, COUNT_MEMBERS, rs -> rs.getInt(1), partyId)
                .orElse(0);
    }

    /** Gói tham số cho {@link #insertMember} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record NewMember(long partyId, long characterId, PartyRole role, Instant joinedAt) {}

    /**
     * {@code false} nếu nhân vật đã ở party khác (UNIQUE {@code party_member.character_id}).
     *
     * <p>⚠️ Race THẬT (xem {@code PartyMembershipRaceIntegrationTest}): 1 nhân vật vào 2 party cùng
     * lúc — chấp nhận 2 lời mời, tạo party trong lúc chấp nhận lời mời, hoặc tạo party 2 lần. Kiểm
     * "đã ở party chưa" trước đó (application/ hoặc {@link #isAlreadyMember}) không thấy transaction
     * chưa commit của bên kia. Bản cũ để INSERT ném unique_violation → {@code
     * DatabaseAccessException} chung chung → client nhận {@code error.server_internal} thay vì
     * "đã ở trong nhóm". {@code ON CONFLICT DO NOTHING} (không phải bắt unique_violation như {@code
     * DuplicateGuildMembershipException} của guild) vì nhánh chấp nhận cần TIẾP TỤC transaction để
     * xoá lời mời — unique_violation đánh dấu cả transaction là aborted, phải thêm SAVEPOINT.
     * Postgres vẫn CHỜ bên kia commit/rollback trước khi quyết định có xung đột hay không, nên kết
     * quả luôn đúng.
     */
    private static boolean insertMember(Connection connection, NewMember member) {
        return JdbcOperations.update(
                        connection,
                        INSERT_MEMBER,
                        member.partyId(),
                        member.characterId(),
                        member.role().name(),
                        Timestamp.from(member.joinedAt()))
                > 0;
    }

    private static void deleteInvite(Connection connection, long inviteId) {
        JdbcOperations.update(connection, DELETE_INVITE, inviteId);
    }

    @Override
    public boolean rejectInvite(long inviteId, long characterId) {
        return JdbcOperations.update(dataSource, REJECT_INVITE, inviteId, characterId) > 0;
    }

    @Override
    public LeaveResult leaveParty(long characterId) {
        return JdbcTransactionRunner.call(dataSource, connection -> leavePartyInTransaction(connection, characterId));
    }

    private static LeaveResult leavePartyInTransaction(Connection connection, long characterId) {
        Optional<MembershipRow> membership = JdbcOperations.queryOne(
                connection,
                SELECT_MEMBERSHIP_FOR_UPDATE,
                rs -> new MembershipRow(rs.getLong("party_id"), PartyRole.valueOf(rs.getString("role"))),
                characterId);
        if (membership.isEmpty()) {
            return LeaveResult.NOT_IN_PARTY;
        }
        long partyId = membership.get().partyId();
        JdbcOperations.lock(connection, LOCK_PARTY, partyId);
        JdbcOperations.update(connection, DELETE_MEMBER, partyId, characterId);
        if (membership.get().role() != PartyRole.LEADER) {
            return LeaveResult.OK;
        }
        promoteNextLeaderOrDissolve(connection, partyId);
        return LeaveResult.OK;
    }

    private record MembershipRow(long partyId, PartyRole role) {}

    private static void promoteNextLeaderOrDissolve(Connection connection, long partyId) {
        Optional<Long> nextLeaderId = JdbcOperations.queryOne(
                connection, FIND_EARLIEST_REMAINING_MEMBER, rs -> rs.getLong("character_id"), partyId);
        if (nextLeaderId.isEmpty()) {
            JdbcOperations.update(connection, DELETE_PARTY, partyId);
            return;
        }
        JdbcOperations.update(connection, PROMOTE_TO_LEADER, partyId, nextLeaderId.get());
        JdbcOperations.update(connection, UPDATE_PARTY_LEADER, nextLeaderId.get(), partyId);
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        return JdbcOperations.update(dataSource, KICK, targetCharacterId, leaderCharacterId) > 0;
    }

    private static PartyMember mapMember(ResultSet rs) throws SQLException {
        return new PartyMember(
                rs.getLong("party_id"),
                rs.getLong("character_id"),
                PartyRole.valueOf(rs.getString("role")),
                rs.getTimestamp("joined_at").toInstant());
    }

    private static PartyInvite mapInvite(ResultSet rs) throws SQLException {
        return new PartyInvite(
                rs.getLong("id"),
                rs.getLong("party_id"),
                rs.getLong("invitee_character_id"),
                rs.getTimestamp("invited_at").toInstant());
    }
}
