package dev.kage.server.database.party;

import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link PartyPort} trên PostgreSQL — bảng {@code party}/{@code party_member}/
 * {@code party_invite} (V15). {@link #createParty}, {@link #acceptInvite}, {@link #leaveParty}
 * chạm nhiều bảng nguyên tử bằng transaction thủ công (KHÔNG {@code TransactionRunner} riêng —
 * xem javadoc {@link PartyPort}).
 */
public final class PostgresPartyAdapter implements PartyPort {

    // Mã lỗi Postgres cho unique_violation (SQLState).
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

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
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_MEMBERSHIP)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapMember(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra thành viên party: characterId=" + characterId, e);
        }
    }

    @Override
    public List<PartyMember> listMembers(long partyId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_MEMBERS)) {
            statement.setLong(1, partyId);
            var result = new ArrayList<PartyMember>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapMember(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê thành viên party: partyId=" + partyId, e);
        }
    }

    @Override
    public Party createParty(long leaderCharacterId) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var now = Instant.now();
                long partyId;
                try (var insertParty = connection.prepareStatement(INSERT_PARTY)) {
                    insertParty.setLong(1, leaderCharacterId);
                    insertParty.setTimestamp(2, Timestamp.from(now));
                    try (ResultSet rs = insertParty.executeQuery()) {
                        rs.next();
                        partyId = rs.getLong(1);
                    }
                }
                insertMember(connection, new NewMember(partyId, leaderCharacterId, PartyRole.LEADER, now));
                connection.commit();
                return new Party(partyId, leaderCharacterId, now);
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi tạo party: leaderCharacterId=" + leaderCharacterId, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tạo party: leaderCharacterId=" + leaderCharacterId, e);
        }
    }

    @Override
    public Optional<PartyInvite> findPendingInviteOfInvitee(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_PENDING_INVITE_OF_INVITEE)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapInvite(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra lời mời party: characterId=" + characterId, e);
        }
    }

    @Override
    public List<PartyInvite> listPendingInvitesOfInvitee(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_PENDING_INVITES_OF_INVITEE)) {
            statement.setLong(1, characterId);
            var result = new ArrayList<PartyInvite>();
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    result.add(mapInvite(rs));
                }
            }
            return result;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê lời mời party: characterId=" + characterId, e);
        }
    }

    @Override
    public PartyInvite invite(long partyId, long inviteeCharacterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(INSERT_INVITE)) {
            statement.setLong(1, partyId);
            statement.setLong(2, inviteeCharacterId);
            var now = Instant.now();
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new PartyInvite(rs.getLong(1), partyId, inviteeCharacterId, now);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new DuplicatePartyInviteException(partyId, inviteeCharacterId);
            }
            throw new DatabaseAccessException(
                    "Lỗi mời party: partyId=" + partyId + ", inviteeCharacterId=" + inviteeCharacterId, e);
        }
    }

    @Override
    public AcceptResult acceptInvite(long inviteId, long characterId) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var result = acceptInviteInTransaction(connection, inviteId, characterId);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi chấp nhận lời mời party: inviteId=" + inviteId, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi chấp nhận lời mời party: inviteId=" + inviteId, e);
        }
    }

    private static AcceptResult acceptInviteInTransaction(Connection connection, long inviteId, long characterId)
            throws SQLException {
        long partyId;
        try (var select = connection.prepareStatement(SELECT_INVITE_FOR_ACCEPT)) {
            select.setLong(1, inviteId);
            select.setLong(2, characterId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return AcceptResult.NOT_FOUND;
                }
                partyId = rs.getLong("party_id");
            }
        }
        try (var lockParty = connection.prepareStatement(LOCK_PARTY)) {
            lockParty.setLong(1, partyId);
            lockParty.executeQuery();
        }
        if (isAlreadyMember(connection, characterId)) {
            deleteInvite(connection, inviteId);
            return AcceptResult.ALREADY_IN_PARTY;
        }
        if (countMembers(connection, partyId) >= Party.MAX_MEMBERS) {
            deleteInvite(connection, inviteId);
            return AcceptResult.PARTY_FULL;
        }
        insertMember(connection, new NewMember(partyId, characterId, PartyRole.MEMBER, Instant.now()));
        deleteInvite(connection, inviteId);
        return AcceptResult.OK;
    }

    private static boolean isAlreadyMember(Connection connection, long characterId) throws SQLException {
        try (var statement = connection.prepareStatement(FIND_MEMBERSHIP)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static int countMembers(Connection connection, long partyId) throws SQLException {
        try (var statement = connection.prepareStatement(COUNT_MEMBERS)) {
            statement.setLong(1, partyId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Gói tham số cho {@link #insertMember} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record NewMember(long partyId, long characterId, PartyRole role, Instant joinedAt) {
    }

    private static void insertMember(Connection connection, NewMember member) throws SQLException {
        try (var statement = connection.prepareStatement(INSERT_MEMBER)) {
            statement.setLong(1, member.partyId());
            statement.setLong(2, member.characterId());
            statement.setString(3, member.role().name());
            statement.setTimestamp(4, Timestamp.from(member.joinedAt()));
            statement.executeUpdate();
        }
    }

    private static void deleteInvite(Connection connection, long inviteId) throws SQLException {
        try (var statement = connection.prepareStatement(DELETE_INVITE)) {
            statement.setLong(1, inviteId);
            statement.executeUpdate();
        }
    }

    @Override
    public boolean rejectInvite(long inviteId, long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(REJECT_INVITE)) {
            statement.setLong(1, inviteId);
            statement.setLong(2, characterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi từ chối lời mời party: inviteId=" + inviteId, e);
        }
    }

    @Override
    public LeaveResult leaveParty(long characterId) {
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                var result = leavePartyInTransaction(connection, characterId);
                connection.commit();
                return result;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi rời party: characterId=" + characterId, e);
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi rời party: characterId=" + characterId, e);
        }
    }

    private static LeaveResult leavePartyInTransaction(Connection connection, long characterId) throws SQLException {
        long partyId;
        PartyRole role;
        try (var select = connection.prepareStatement(SELECT_MEMBERSHIP_FOR_UPDATE)) {
            select.setLong(1, characterId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return LeaveResult.NOT_IN_PARTY;
                }
                partyId = rs.getLong("party_id");
                role = PartyRole.valueOf(rs.getString("role"));
            }
        }
        try (var lockParty = connection.prepareStatement(LOCK_PARTY)) {
            lockParty.setLong(1, partyId);
            lockParty.executeQuery();
        }
        try (var delete = connection.prepareStatement(DELETE_MEMBER)) {
            delete.setLong(1, partyId);
            delete.setLong(2, characterId);
            delete.executeUpdate();
        }
        if (role != PartyRole.LEADER) {
            return LeaveResult.OK;
        }
        promoteNextLeaderOrDissolve(connection, partyId);
        return LeaveResult.OK;
    }

    private static void promoteNextLeaderOrDissolve(Connection connection, long partyId) throws SQLException {
        Long nextLeaderId = null;
        try (var select = connection.prepareStatement(FIND_EARLIEST_REMAINING_MEMBER)) {
            select.setLong(1, partyId);
            try (ResultSet rs = select.executeQuery()) {
                if (rs.next()) {
                    nextLeaderId = rs.getLong("character_id");
                }
            }
        }
        if (nextLeaderId == null) {
            try (var deleteParty = connection.prepareStatement(DELETE_PARTY)) {
                deleteParty.setLong(1, partyId);
                deleteParty.executeUpdate();
            }
            return;
        }
        try (var promote = connection.prepareStatement(PROMOTE_TO_LEADER)) {
            promote.setLong(1, partyId);
            promote.setLong(2, nextLeaderId);
            promote.executeUpdate();
        }
        try (var updateLeader = connection.prepareStatement(UPDATE_PARTY_LEADER)) {
            updateLeader.setLong(1, nextLeaderId);
            updateLeader.setLong(2, partyId);
            updateLeader.executeUpdate();
        }
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(KICK)) {
            statement.setLong(1, targetCharacterId);
            statement.setLong(2, leaderCharacterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi kick thành viên party: leaderCharacterId=" + leaderCharacterId
                            + ", targetCharacterId=" + targetCharacterId, e);
        }
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
