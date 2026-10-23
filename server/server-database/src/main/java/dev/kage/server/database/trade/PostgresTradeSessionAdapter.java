package dev.kage.server.database.trade;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.JdbcTransactionRunner;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeOfferItem;
import dev.kage.trade.domain.TradeParticipantBusyException;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionPort;
import dev.kage.trade.domain.TradeSessionSnapshot;
import dev.kage.trade.domain.TradeStatus;
import dev.kage.trade.domain.Transaction;

/**
 * Hiện thực {@link TradeSessionPort} trên PostgreSQL — bảng {@code trade_session}/{@code
 * trade_offer_item} (V16). {@link #replaceOffer} chạm 2 bảng nguyên tử bằng transaction thủ công
 * trên 1 connection (KHÔNG {@code TransactionRunner} riêng — cả 2 bảng đều thuộc feature này,
 * cùng lý do {@code GuildPort}/{@code PartyPort} không cần). Không có case cần bắt
 * {@code unique_violation} giữa transaction rồi tiếp tục (khác {@code PostgresGuildAdapter}) nên
 * {@link #replaceOfferInTransaction} vẫn dùng {@link JdbcOperations} được — {@link #replaceOffer}
 * chỉ cần catch thêm {@link RuntimeException} ở ranh giới transaction để rollback đúng trước khi
 * rethrow.
 */
public final class PostgresTradeSessionAdapter implements TradeSessionPort {

    private static final String FIND_ACTIVE_OF_CHARACTER = """
            SELECT id, requester_character_id, target_character_id, status, created_at FROM trade_session
             WHERE requester_character_id = ? OR target_character_id = ?
            """;

    private static final String LOCK_PARTICIPANT = "SELECT pg_advisory_xact_lock(?, ?)";

    // "Không gian tên" riêng ("TRAD") cho khoá tư vấn của #createRequest — không đụng túi đồ
    // (0x494E5654) hay friendship (0x46524E44). Hằng số CỐ ĐỊNH, cùng lý do rolling deploy ở
    // CharacterInventoryLock.
    private static final int TRADE_PARTICIPANT_LOCK_NAMESPACE = 0x5452_4144;

    private static final String INSERT_SESSION = """
            INSERT INTO trade_session (requester_character_id, target_character_id, status, created_at)
            VALUES (?, ?, 'PENDING_ACCEPT', ?) RETURNING id
            """;

    private static final String ACCEPT_REQUEST = """
            UPDATE trade_session SET status = 'OPEN'
             WHERE id = ? AND target_character_id = ? AND status = 'PENDING_ACCEPT'
            """;

    private static final String DELETE_IF_PARTICIPANT = """
            DELETE FROM trade_session WHERE id = ? AND (requester_character_id = ? OR target_character_id = ?)
            """;

    private static final String SELECT_SESSION_FOR_OFFER = """
            SELECT requester_character_id, target_character_id, status FROM trade_session WHERE id = ? FOR UPDATE
            """;

    private static final String DELETE_OFFER_ITEMS_OF_CHARACTER = """
            DELETE FROM trade_offer_item WHERE session_id = ? AND offering_character_id = ?
            """;

    private static final String INSERT_OFFER_ITEM = """
            INSERT INTO trade_offer_item (session_id, offering_character_id, item_id, quantity) VALUES (?, ?, ?, ?)
            """;

    private static final String UPDATE_REQUESTER_GOLD_AND_RESET_CONFIRM = """
            UPDATE trade_session SET requester_gold = ?, requester_confirmed = FALSE, target_confirmed = FALSE,
                   offer_version = offer_version + 1
             WHERE id = ?
            """;

    private static final String UPDATE_TARGET_GOLD_AND_RESET_CONFIRM = """
            UPDATE trade_session SET target_gold = ?, requester_confirmed = FALSE, target_confirmed = FALSE,
                   offer_version = offer_version + 1
             WHERE id = ?
            """;

    private static final String SELECT_SESSION = """
            SELECT id, requester_character_id, target_character_id, status, requester_gold, target_gold,
                   requester_confirmed, target_confirmed, offer_version
              FROM trade_session WHERE id = ?
            """;

    private static final String SELECT_SESSION_FOR_UPDATE = SELECT_SESSION + " FOR UPDATE";

    private static final String SELECT_OFFER_ITEMS = """
            SELECT id, session_id, offering_character_id, item_id, quantity FROM trade_offer_item WHERE session_id = ?
            """;

    private static final String SET_CONFIRMED = """
            UPDATE trade_session
               SET requester_confirmed = CASE WHEN requester_character_id = ? THEN TRUE ELSE requester_confirmed END,
                   target_confirmed = CASE WHEN target_character_id = ? THEN TRUE ELSE target_confirmed END
             WHERE id = ?
            """;

    private static final String DELETE_ALL_SESSIONS = """
            DELETE FROM trade_session
            """;

    private static final String DELETE_SESSION = """
            DELETE FROM trade_session WHERE id = ?
            """;

    private final DataSource dataSource;

    public PostgresTradeSessionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<TradeSession> findActiveSessionOfCharacter(long characterId) {
        return JdbcOperations.queryOne(
                dataSource,
                FIND_ACTIVE_OF_CHARACTER,
                PostgresTradeSessionAdapter::mapSession,
                characterId,
                characterId);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Khoá tư vấn theo TỪNG nhân vật (không theo cặp như {@code PostgresFriendAdapter}): luật bị
     * phá là "T chỉ ở một phiên", và hai lời mời A→T, B→T là hai CẶP khác nhau — khoá cặp sẽ không
     * bắt chúng xếp hàng. Lúc này chưa có dòng nào để {@code FOR UPDATE}, nên phải là khoá tư vấn.
     * Hai khoá xin theo thứ tự khoá tăng dần (như {@code CharacterInventoryLock#acquireBoth}) nên
     * hai lời mời chéo nhau không chờ vòng. Giao dịch này không giữ khoá nào khác — không chạm thứ
     * tự khoá chung của DATABASE.md §5.1.
     */
    @Override
    public TradeSession createRequest(long requesterCharacterId, long targetCharacterId) {
        Instant now = Instant.now();
        return JdbcTransactionRunner.call(dataSource, connection -> {
            lockParticipants(connection, requesterCharacterId, targetCharacterId);
            if (hasActiveSession(connection, requesterCharacterId) || hasActiveSession(connection, targetCharacterId)) {
                throw new TradeParticipantBusyException(requesterCharacterId, targetCharacterId);
            }
            long sessionId = JdbcOperations.insertReturningId(
                    connection, INSERT_SESSION, requesterCharacterId, targetCharacterId, Timestamp.from(now));
            return new TradeSession(
                    sessionId, requesterCharacterId, targetCharacterId, TradeStatus.PENDING_ACCEPT, now);
        });
    }

    private static void lockParticipants(Connection connection, long characterIdA, long characterIdB) {
        int keyA = Long.hashCode(characterIdA);
        int keyB = Long.hashCode(characterIdB);
        JdbcOperations.lock(connection, LOCK_PARTICIPANT, TRADE_PARTICIPANT_LOCK_NAMESPACE, Math.min(keyA, keyB));
        if (keyA != keyB) {
            JdbcOperations.lock(connection, LOCK_PARTICIPANT, TRADE_PARTICIPANT_LOCK_NAMESPACE, Math.max(keyA, keyB));
        }
    }

    private static boolean hasActiveSession(Connection connection, long characterId) {
        return JdbcOperations.queryOne(
                        connection,
                        FIND_ACTIVE_OF_CHARACTER,
                        PostgresTradeSessionAdapter::mapSession,
                        characterId,
                        characterId)
                .isPresent();
    }

    @Override
    public boolean acceptRequest(long sessionId, long characterId) {
        return JdbcOperations.update(dataSource, ACCEPT_REQUEST, sessionId, characterId) > 0;
    }

    @Override
    public boolean deleteSessionIfParticipant(long sessionId, long characterId) {
        return JdbcOperations.update(dataSource, DELETE_IF_PARTICIPANT, sessionId, characterId, characterId) > 0;
    }

    @Override
    public int deleteAllSessions() {
        return JdbcOperations.update(dataSource, DELETE_ALL_SESSIONS);
    }

    // PMD AvoidCatchingGenericException: đúng ý — ranh giới transaction PHẢI rollback khi action
    // ném BẤT KỲ RuntimeException nào (VD DatabaseAccessException từ JdbcOperations), cùng tinh
    // thần JdbcTransactionRunner.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    @Override
    public boolean replaceOffer(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                boolean ok = replaceOfferInTransaction(
                        connection, new OfferRequest(sessionId, characterId, goldAmount, items));
                if (ok) {
                    connection.commit();
                } else {
                    connection.rollback();
                }
                return ok;
            } catch (SQLException e) {
                connection.rollback();
                throw new DatabaseAccessException("Lỗi đặt lời chào trade: sessionId=" + sessionId, e);
            } catch (RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đặt lời chào trade: sessionId=" + sessionId, e);
        }
    }

    /** Gói tham số cho {@link #replaceOfferInTransaction} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record OfferRequest(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {}

    private record SessionForOfferRow(long requesterId, long targetId, String status) {}

    private static boolean replaceOfferInTransaction(Connection connection, OfferRequest request) {
        Optional<SessionForOfferRow> session = JdbcOperations.queryOne(
                connection,
                SELECT_SESSION_FOR_OFFER,
                rs -> new SessionForOfferRow(
                        rs.getLong("requester_character_id"),
                        rs.getLong("target_character_id"),
                        rs.getString("status")),
                request.sessionId());
        if (session.isEmpty() || !"OPEN".equals(session.get().status())) {
            return false;
        }
        long requesterId = session.get().requesterId();
        long targetId = session.get().targetId();
        long characterId = request.characterId();
        if (characterId != requesterId && characterId != targetId) {
            return false;
        }
        // Quyết định chủ dự án 2026-09-29: món đang chào không được cường hoá. Cường hoá hỏi "đang
        // chào không" SAU khoá túi đồ; lấy CÙNG khoá đó ở đây để hai bên xếp hàng — không có nó,
        // cường hoá hỏi xong (chưa có lời chào), lời chào commit, cường hoá commit sau ⇒ người kia
        // xác nhận trên cấp cũ. Thứ tự khoá: dòng phiên (FOR UPDATE ở trên) rồi túi đồ — đúng ngoại
        // lệ bước 0 của DATABASE.md §5.1, cùng thứ tự ConfirmTradeUseCase.
        CharacterInventoryLock.acquire(connection, characterId);
        JdbcOperations.update(connection, DELETE_OFFER_ITEMS_OF_CHARACTER, request.sessionId(), characterId);
        List<Object[]> batchParams = request.items().stream()
                .map(item -> new Object[] {request.sessionId(), characterId, item.itemId(), item.quantity()})
                .collect(Collectors.toList());
        JdbcOperations.batchUpdate(connection, INSERT_OFFER_ITEM, batchParams);
        String updateGoldSql = characterId == requesterId
                ? UPDATE_REQUESTER_GOLD_AND_RESET_CONFIRM
                : UPDATE_TARGET_GOLD_AND_RESET_CONFIRM;
        JdbcOperations.update(connection, updateGoldSql, request.goldAmount(), request.sessionId());
        return true;
    }

    @Override
    public Optional<TradeSessionSnapshot> findSnapshot(long sessionId) {
        try (Connection connection = dataSource.getConnection()) {
            return readSnapshot(connection, sessionId, SELECT_SESSION);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra trạng thái trade: sessionId=" + sessionId, e);
        }
    }

    @Override
    public Optional<TradeSessionSnapshot> lockForExecution(Transaction tx, long sessionId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        return readSnapshot(connection, sessionId, SELECT_SESSION_FOR_UPDATE);
    }

    private static Optional<TradeSessionSnapshot> readSnapshot(Connection connection, long sessionId, String sql) {
        Optional<SessionRow> row =
                JdbcOperations.queryOne(connection, sql, PostgresTradeSessionAdapter::mapSessionRow, sessionId);
        if (row.isEmpty()) {
            return Optional.empty();
        }
        List<TradeOfferItem> items = JdbcOperations.query(
                connection, SELECT_OFFER_ITEMS, PostgresTradeSessionAdapter::mapOfferItem, sessionId);
        SessionRow r = row.get();
        return Optional.of(new TradeSessionSnapshot(
                sessionId,
                r.requesterId(),
                r.targetId(),
                r.status(),
                r.requesterGold(),
                r.targetGold(),
                r.requesterConfirmed(),
                r.targetConfirmed(),
                items,
                r.offerVersion()));
    }

    /** Kết quả SELECT 1 dòng {@code trade_session} — record header không tính Checkstyle {@code ParameterNumber}. */
    private record SessionRow(
            long requesterId,
            long targetId,
            TradeStatus status,
            long requesterGold,
            long targetGold,
            boolean requesterConfirmed,
            boolean targetConfirmed,
            long offerVersion) {}

    private static SessionRow mapSessionRow(ResultSet rs) throws SQLException {
        return new SessionRow(
                rs.getLong("requester_character_id"),
                rs.getLong("target_character_id"),
                TradeStatus.valueOf(rs.getString("status")),
                rs.getLong("requester_gold"),
                rs.getLong("target_gold"),
                rs.getBoolean("requester_confirmed"),
                rs.getBoolean("target_confirmed"),
                rs.getLong("offer_version"));
    }

    private static TradeOfferItem mapOfferItem(ResultSet rs) throws SQLException {
        return new TradeOfferItem(
                rs.getLong("id"),
                rs.getLong("session_id"),
                rs.getLong("offering_character_id"),
                rs.getLong("item_id"),
                rs.getInt("quantity"));
    }

    @Override
    public void setConfirmed(Transaction tx, long sessionId, long characterId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, SET_CONFIRMED, characterId, characterId, sessionId);
    }

    @Override
    public void deleteSession(Transaction tx, long sessionId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        JdbcOperations.update(connection, DELETE_SESSION, sessionId);
    }

    private static TradeSession mapSession(ResultSet rs) throws SQLException {
        return new TradeSession(
                rs.getLong("id"),
                rs.getLong("requester_character_id"),
                rs.getLong("target_character_id"),
                TradeStatus.valueOf(rs.getString("status")),
                rs.getTimestamp("created_at").toInstant());
    }
}
