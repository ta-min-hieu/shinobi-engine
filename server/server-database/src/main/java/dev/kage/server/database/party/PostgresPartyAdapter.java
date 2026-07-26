package dev.kage.server.database.party;

import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link PartyPort} trên PostgreSQL — bảng {@code party}/{@code party_member}/
 * {@code party_invite} (V15). {@link #createParty}, {@link #acceptInvite}, {@link #leaveParty}
 * chạm nhiều bảng nguyên tử bằng transaction thủ công (KHÔNG {@code TransactionRunner} riêng —
 * xem javadoc {@link PartyPort}). Khác {@code PostgresGuildAdapter}: không có case nào cần bắt
 * {@code unique_violation} GIỮA transaction rồi tiếp tục (party check "đã ở party khác" bằng SELECT
 * trước khi insert, không dựa vào catch conflict) — nên toàn bộ statement trong 3 method này VẪN
 * dùng {@link JdbcOperations} được, chỉ cần catch thêm {@link RuntimeException} ở transaction
 * boundary để rollback đúng trước khi rethrow (JdbcOperations bọc SQLException thành unchecked).
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

    private static final String INSERT_MEMBER = """
            INSERT INTO party_member (party_id, character_id, role, joined_at) VALUES (?, ?, ?, ?)
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

    // PMD AvoidCatchingGenericException: đúng ý — ranh giới transaction PHẢI rollback khi action
    // ném BẤT KỲ RuntimeException nào (VD DatabaseAccessException từ JdbcOperations), cùng tinh
    // thần JdbcTransactionRunner.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public Party createParty(long leaderCharacterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Instant now = Instant.now();
                long partyId = JdbcOperations.insertReturningId(
                        connection, INSERT_PARTY, leaderCharacterId, Timestamp.from(now));
                insertMember(connection, new NewMember(partyId, leaderCharacterId, PartyRole.LEADER, now));
                connection.commit();
                return new Party(partyId, leaderCharacterId, now);
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi tạo party: leaderCharacterId=" + leaderCharacterId, e);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tạo party: leaderCharacterId=" + leaderCharacterId, e);
        }
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

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public AcceptResult acceptInvite(long inviteId, long characterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                AcceptResult result = acceptInviteInTransaction(connection, inviteId, characterId);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi chấp nhận lời mời party: inviteId=" + inviteId, e);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi chấp nhận lời mời party: inviteId=" + inviteId, e);
        }
    }

    private static AcceptResult acceptInviteInTransaction(Connection connection, long inviteId, long characterId) {
        Optional<Long> partyId = JdbcOperations.queryOne(
                connection, SELECT_INVITE_FOR_ACCEPT, rs -> rs.getLong("party_id"), inviteId, characterId);
        if (partyId.isEmpty()) {
            return AcceptResult.NOT_FOUND;
        }
        JdbcOperations.query(connection, LOCK_PARTY, rs -> Boolean.TRUE, partyId.get());
        if (isAlreadyMember(connection, characterId)) {
            deleteInvite(connection, inviteId);
            return AcceptResult.ALREADY_IN_PARTY;
        }
        if (countMembers(connection, partyId.get()) >= Party.MAX_MEMBERS) {
            deleteInvite(connection, inviteId);
            return AcceptResult.PARTY_FULL;
        }
        insertMember(connection, new NewMember(partyId.get(), characterId, PartyRole.MEMBER, Instant.now()));
        deleteInvite(connection, inviteId);
        return AcceptResult.OK;
    }

    private static boolean isAlreadyMember(Connection connection, long characterId) {
        return !JdbcOperations.query(connection, FIND_MEMBERSHIP, rs -> Boolean.TRUE, characterId).isEmpty();
    }

    private static int countMembers(Connection connection, long partyId) {
        return JdbcOperations.queryOne(connection, COUNT_MEMBERS, rs -> rs.getInt(1), partyId).orElse(0);
    }

    /** Gói tham số cho {@link #insertMember} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record NewMember(long partyId, long characterId, PartyRole role, Instant joinedAt) {
    }

    private static void insertMember(Connection connection, NewMember member) {
        JdbcOperations.update(
                connection,
                INSERT_MEMBER,
                member.partyId(),
                member.characterId(),
                member.role().name(),
                Timestamp.from(member.joinedAt()));
    }

    private static void deleteInvite(Connection connection, long inviteId) {
        JdbcOperations.update(connection, DELETE_INVITE, inviteId);
    }

    @Override
    public boolean rejectInvite(long inviteId, long characterId) {
        return JdbcOperations.update(dataSource, REJECT_INVITE, inviteId, characterId) > 0;
    }

    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public LeaveResult leaveParty(long characterId) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                LeaveResult result = leavePartyInTransaction(connection, characterId);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi rời party: characterId=" + characterId, e);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi rời party: characterId=" + characterId, e);
        }
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
        JdbcOperations.query(connection, LOCK_PARTY, rs -> Boolean.TRUE, partyId);
        JdbcOperations.update(connection, DELETE_MEMBER, partyId, characterId);
        if (membership.get().role() != PartyRole.LEADER) {
            return LeaveResult.OK;
        }
        promoteNextLeaderOrDissolve(connection, partyId);
        return LeaveResult.OK;
    }

    private record MembershipRow(long partyId, PartyRole role) {
    }

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
                rs.getLong("id"), rs.getLong("party_id"), rs.getLong("invitee_character_id"),
                rs.getTimestamp("invited_at").toInstant());
    }
}
