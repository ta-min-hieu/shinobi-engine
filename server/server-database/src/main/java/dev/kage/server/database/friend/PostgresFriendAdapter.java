package dev.kage.server.database.friend;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.friend.domain.DuplicateFriendRequestException;
import dev.kage.friend.domain.FriendPort;
import dev.kage.friend.domain.Friendship;
import dev.kage.friend.domain.FriendshipStatus;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;

/**
 * Hiện thực {@link FriendPort} trên PostgreSQL — bảng {@code friendship} (V12). Không có
 * {@code Transaction} — mỗi method tự mở connection ngắn hạn, đúng như javadoc {@link FriendPort}
 * đã giải thích (không có gold/item cần nguyên tử hoá xuyên nhiều bảng).
 */
public final class PostgresFriendAdapter implements FriendPort {

    private static final String FIND_BETWEEN = """
            SELECT id, requester_id, addressee_id, status, created_at, responded_at FROM friendship
             WHERE (requester_id = ? AND addressee_id = ?) OR (requester_id = ? AND addressee_id = ?)
            """;

    // Khoá tư vấn (advisory) theo CẶP nhân vật, tự nhả khi giao dịch kết thúc. Dạng 2 tham số int:
    // tham số 1 là "không gian tên" riêng của bảng friendship để không đụng khoá tư vấn nào khác
    // trong DB; tham số 2 là băm của cặp (min, max) — trùng băm giữa 2 cặp khác nhau chỉ làm chúng
    // xếp hàng chờ nhau một chút, không bao giờ sai kết quả.
    private static final String LOCK_PAIR = "SELECT pg_advisory_xact_lock(?, ?)";

    // Hằng số tuỳ ý nhưng CỐ ĐỊNH — đổi giá trị trong lúc server cũ còn chạy song song (rolling
    // deploy) sẽ làm 2 bản khoá 2 chìa khác nhau cho cùng một cặp.
    private static final int FRIENDSHIP_LOCK_NAMESPACE = 0x4652_4E44;

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

    /**
     * {@inheritDoc}
     *
     * <p>UNIQUE (requester_id, addressee_id) của V12 chỉ chặn trùng CÙNG CHIỀU. A mời B đúng lúc B
     * mời A thì cả hai {@code findBetween} ở use case đều thấy rỗng và cả hai INSERT đều lọt, sinh 2
     * dòng cho 1 cặp ⇒ sau khi cả hai chấp nhận, mỗi người thấy người kia HAI LẦN trong danh sách
     * bạn. Vì vậy phép kiểm "đã có quan hệ theo chiều nào chưa" phải nằm CÙNG giao dịch với INSERT,
     * sau một khoá theo cặp — khoá dòng không dùng được vì lúc đó chưa có dòng nào để khoá.
     */
    @Override
    public Friendship sendRequest(long requesterId, long addresseeId) {
        Instant now = Instant.now();
        return JdbcTransactionRunner.call(dataSource, connection -> {
            JdbcOperations.query(
                    connection,
                    LOCK_PAIR,
                    rs -> Boolean.TRUE,
                    FRIENDSHIP_LOCK_NAMESPACE,
                    pairLockKey(requesterId, addresseeId));
            if (JdbcOperations.queryOne(
                            connection,
                            FIND_BETWEEN,
                            PostgresFriendAdapter::mapRow,
                            requesterId,
                            addresseeId,
                            addresseeId,
                            requesterId)
                    .isPresent()) {
                throw new DuplicateFriendRequestException(requesterId, addresseeId);
            }
            long id = JdbcOperations.insertReturningIdOrThrow(
                    connection,
                    INSERT_REQUEST,
                    () -> new DuplicateFriendRequestException(requesterId, addresseeId),
                    requesterId,
                    addresseeId,
                    Timestamp.from(now));
            return new Friendship(id, requesterId, addresseeId, FriendshipStatus.PENDING, now, null);
        });
    }

    // Không phụ thuộc chiều: (a, b) và (b, a) ra cùng một chìa.
    private static int pairLockKey(long characterIdA, long characterIdB) {
        long low = Math.min(characterIdA, characterIdB);
        long high = Math.max(characterIdA, characterIdB);
        return Long.hashCode(low * 31 + high);
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
        return JdbcOperations.update(dataSource, REMOVE, characterId, friendCharacterId, friendCharacterId, characterId)
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
