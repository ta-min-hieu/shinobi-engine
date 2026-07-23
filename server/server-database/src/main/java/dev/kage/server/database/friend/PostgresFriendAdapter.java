package dev.kage.server.database.friend;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.server.database.DatabaseAccessException;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link FriendPort} trên PostgreSQL — bảng {@code friendship} (V12). Không có
 * {@code Transaction} — mỗi method tự mở connection ngắn hạn, đúng như javadoc {@link FriendPort}
 * đã giải thích (không có gold/item cần nguyên tử hoá xuyên nhiều bảng).
 */
public final class PostgresFriendAdapter implements FriendPort {

    // Mã lỗi Postgres cho unique_violation (SQLState).
    private static final String UNIQUE_VIOLATION_SQLSTATE = "23505";

    private static final String FIND_BETWEEN = """
            SELECT id, requester_id, addressee_id, status, created_at, responded_at FROM friendship
             WHERE (requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?)
            """;

    private static final String INSERT_REQUEST = """
            INSERT INTO friendship (requester_id, addressee_id, status, created_at)
            VALUES (?, ?, 'PENDING', ?)
            RETURNING id
            """;

    private static final String ACCEPT = """
            UPDATE friendship SET status = 'ACCEPTED', responded_at = ?
             WHERE id = ? AND addressee_id = ? AND status = 'PENDING'
            """;

    private static final String REJECT = """
            DELETE FROM friendship WHERE id = ? AND addressee_id = ? AND status = 'PENDING'
            """;

    private static final String REMOVE = """
            DELETE FROM friendship
             WHERE status = 'ACCEPTED'
               AND ((requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?))
            """;

    private static final String LIST_ACCEPTED = """
            SELECT id, requester_id, addressee_id, status, created_at, responded_at FROM friendship
             WHERE status = 'ACCEPTED' AND (requester_id = ? OR addressee_id = ?)
            """;

    private static final String LIST_PENDING_INCOMING = """
            SELECT id, requester_id, addressee_id, status, created_at, responded_at FROM friendship
             WHERE status = 'PENDING' AND addressee_id = ?
            """;

    private final DataSource dataSource;

    public PostgresFriendAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<Friendship> findBetween(long characterIdA, long characterIdB) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BETWEEN)) {
            statement.setLong(1, characterIdA);
            statement.setLong(2, characterIdB);
            statement.setLong(3, characterIdB);
            statement.setLong(4, characterIdA);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi tra quan hệ bạn bè: " + characterIdA + ", " + characterIdB, e);
        }
    }

    @Override
    public Friendship sendRequest(long requesterId, long addresseeId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(INSERT_REQUEST)) {
            statement.setLong(1, requesterId);
            statement.setLong(2, addresseeId);
            var now = Instant.now();
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Friendship(rs.getLong(1), requesterId, addresseeId, FriendshipStatus.PENDING, now, null);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new DuplicateFriendRequestException(requesterId, addresseeId);
            }
            throw new DatabaseAccessException(
                    "Lỗi gửi lời mời kết bạn: " + requesterId + " -> " + addresseeId, e);
        }
    }

    @Override
    public boolean accept(long friendshipId, long addresseeId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(ACCEPT)) {
            statement.setTimestamp(1, Timestamp.from(Instant.now()));
            statement.setLong(2, friendshipId);
            statement.setLong(3, addresseeId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi chấp nhận kết bạn: friendshipId=" + friendshipId, e);
        }
    }

    @Override
    public boolean reject(long friendshipId, long addresseeId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(REJECT)) {
            statement.setLong(1, friendshipId);
            statement.setLong(2, addresseeId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi từ chối kết bạn: friendshipId=" + friendshipId, e);
        }
    }

    @Override
    public boolean remove(long characterId, long friendCharacterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(REMOVE)) {
            statement.setLong(1, characterId);
            statement.setLong(2, friendCharacterId);
            statement.setLong(3, friendCharacterId);
            statement.setLong(4, characterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi huỷ kết bạn: " + characterId + ", " + friendCharacterId, e);
        }
    }

    @Override
    public List<Friendship> listAccepted(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_ACCEPTED)) {
            statement.setLong(1, characterId);
            statement.setLong(2, characterId);
            return mapRows(statement);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê bạn bè: characterId=" + characterId, e);
        }
    }

    @Override
    public List<Friendship> listPendingIncoming(long characterId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(LIST_PENDING_INCOMING)) {
            statement.setLong(1, characterId);
            return mapRows(statement);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi liệt kê lời mời kết bạn: characterId=" + characterId, e);
        }
    }

    private static List<Friendship> mapRows(PreparedStatement statement) throws SQLException {
        try (ResultSet rs = statement.executeQuery()) {
            var result = new ArrayList<Friendship>();
            while (rs.next()) {
                result.add(mapRow(rs));
            }
            return result;
        }
    }

    private static Friendship mapRow(ResultSet rs) throws SQLException {
        var respondedAt = rs.getTimestamp("responded_at");
        return new Friendship(
                rs.getLong("id"),
                rs.getLong("requester_id"),
                rs.getLong("addressee_id"),
                FriendshipStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                respondedAt == null ? null : respondedAt.toInstant());
    }
}
