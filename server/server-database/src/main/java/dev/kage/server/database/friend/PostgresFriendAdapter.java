package dev.kage.server.database.friend;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
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
        return JdbcOperations.queryOne(
                dataSource,
                FIND_BETWEEN,
                PostgresFriendAdapter::mapRow,
                characterIdA,
                characterIdB,
                characterIdB,
                characterIdA);
    }

    // Bẫy unique_violation → DuplicateFriendRequestException là logic nghiệp vụ đặc thù, không đưa
    // vào JdbcOperations dùng chung.
    @Override
    public Friendship sendRequest(long requesterId, long addresseeId) {
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_REQUEST)) {
            statement.setLong(1, requesterId);
            statement.setLong(2, addresseeId);
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new Friendship(rs.getLong(1), requesterId, addresseeId, FriendshipStatus.PENDING, now, null);
            }
        } catch (SQLException e) {
            if (UNIQUE_VIOLATION_SQLSTATE.equals(e.getSQLState())) {
                throw new DuplicateFriendRequestException(requesterId, addresseeId);
            }
            throw new DatabaseAccessException("Lỗi gửi lời mời kết bạn: " + requesterId + " -> " + addresseeId, e);
        }
    }

    @Override
    public boolean accept(long friendshipId, long addresseeId) {
        return JdbcOperations.update(dataSource, ACCEPT, Timestamp.from(Instant.now()), friendshipId, addresseeId) > 0;
    }

    @Override
    public boolean reject(long friendshipId, long addresseeId) {
        return JdbcOperations.update(dataSource, REJECT, friendshipId, addresseeId) > 0;
    }

    @Override
    public boolean remove(long characterId, long friendCharacterId) {
        return JdbcOperations.update(
                        dataSource, REMOVE, characterId, friendCharacterId, friendCharacterId, characterId)
                > 0;
    }

    @Override
    public List<Friendship> listAccepted(long characterId) {
        return JdbcOperations.query(dataSource, LIST_ACCEPTED, PostgresFriendAdapter::mapRow, characterId, characterId);
    }

    @Override
    public List<Friendship> listPendingIncoming(long characterId) {
        return JdbcOperations.query(dataSource, LIST_PENDING_INCOMING, PostgresFriendAdapter::mapRow, characterId);
    }

    private static Friendship mapRow(ResultSet rs) throws SQLException {
        Timestamp respondedAt = rs.getTimestamp("responded_at");
        return new Friendship(
                rs.getLong("id"),
                rs.getLong("requester_id"),
                rs.getLong("addressee_id"),
                FriendshipStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant(),
                respondedAt == null ? null : respondedAt.toInstant());
    }
}
