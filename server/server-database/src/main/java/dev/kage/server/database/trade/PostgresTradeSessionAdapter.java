package dev.kage.server.database.trade;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeOfferItem;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionPort;
import dev.kage.trade.domain.TradeSessionSnapshot;
import dev.kage.trade.domain.TradeStatus;
import java.sql.Connection;
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
 * Hiện thực {@link TradeSessionPort} trên PostgreSQL — bảng {@code trade_session}/{@code
 * trade_offer_item} (V16). {@link #replaceOffer} chạm 2 bảng nguyên tử bằng transaction thủ công
 * trên 1 connection (KHÔNG {@code TransactionRunner} riêng — cả 2 bảng đều thuộc feature này,
 * cùng lý do {@code GuildPort}/{@code PartyPort} không cần).
 */
public final class PostgresTradeSessionAdapter implements TradeSessionPort {

    private static final String FIND_ACTIVE_OF_CHARACTER = """
            SELECT id, requester_character_id, target_character_id, status, created_at FROM trade_session
             WHERE requester_character_id = ? OR target_character_id = ?
            """;

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
            UPDATE trade_session SET requester_gold = ?, requester_confirmed = FALSE, target_confirmed = FALSE
             WHERE id = ?
            """;

    private static final String UPDATE_TARGET_GOLD_AND_RESET_CONFIRM = """
            UPDATE trade_session SET target_gold = ?, requester_confirmed = FALSE, target_confirmed = FALSE
             WHERE id = ?
            """;

    private static final String SELECT_SESSION = """
            SELECT id, requester_character_id, target_character_id, status, requester_gold, target_gold,
                   requester_confirmed, target_confirmed
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

    private static final String DELETE_SESSION = """
            DELETE FROM trade_session WHERE id = ?
            """;

    private final DataSource dataSource;

    public PostgresTradeSessionAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<TradeSession> findActiveSessionOfCharacter(long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_ACTIVE_OF_CHARACTER)) {
            statement.setLong(1, characterId);
            statement.setLong(2, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapSession(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra phiên trade: characterId=" + characterId, e);
        }
    }

    @Override
    public TradeSession createRequest(long requesterCharacterId, long targetCharacterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_SESSION)) {
            statement.setLong(1, requesterCharacterId);
            statement.setLong(2, targetCharacterId);
            Instant now = Instant.now();
            statement.setTimestamp(3, Timestamp.from(now));
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return new TradeSession(
                        rs.getLong(1), requesterCharacterId, targetCharacterId, TradeStatus.PENDING_ACCEPT, now);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException(
                    "Lỗi tạo yêu cầu trade: requesterCharacterId=" + requesterCharacterId, e);
        }
    }

    @Override
    public boolean acceptRequest(long sessionId, long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(ACCEPT_REQUEST)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, characterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi chấp nhận trade: sessionId=" + sessionId, e);
        }
    }

    @Override
    public boolean deleteSessionIfParticipant(long sessionId, long characterId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(DELETE_IF_PARTICIPANT)) {
            statement.setLong(1, sessionId);
            statement.setLong(2, characterId);
            statement.setLong(3, characterId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi huỷ trade: sessionId=" + sessionId, e);
        }
    }

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
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đặt lời chào trade: sessionId=" + sessionId, e);
        }
    }

    /** Gói tham số cho {@link #replaceOfferInTransaction} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    private record OfferRequest(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {
    }

    private static boolean replaceOfferInTransaction(Connection connection, OfferRequest request)
            throws SQLException {
        long requesterId;
        long targetId;
        try (PreparedStatement select = connection.prepareStatement(SELECT_SESSION_FOR_OFFER)) {
            select.setLong(1, request.sessionId());
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next() || !"OPEN".equals(rs.getString("status"))) {
                    return false;
                }
                requesterId = rs.getLong("requester_character_id");
                targetId = rs.getLong("target_character_id");
            }
        }
        long characterId = request.characterId();
        if (characterId != requesterId && characterId != targetId) {
            return false;
        }
        try (PreparedStatement delete = connection.prepareStatement(DELETE_OFFER_ITEMS_OF_CHARACTER)) {
            delete.setLong(1, request.sessionId());
            delete.setLong(2, characterId);
            delete.executeUpdate();
        }
        try (PreparedStatement insert = connection.prepareStatement(INSERT_OFFER_ITEM)) {
            for (OfferedItemInput item : request.items()) {
                insert.setLong(1, request.sessionId());
                insert.setLong(2, characterId);
                insert.setLong(3, item.itemId());
                insert.setInt(4, item.quantity());
                insert.addBatch();
            }
            if (!request.items().isEmpty()) {
                insert.executeBatch();
            }
        }
        String updateGoldSql = characterId == requesterId
                ? UPDATE_REQUESTER_GOLD_AND_RESET_CONFIRM
                : UPDATE_TARGET_GOLD_AND_RESET_CONFIRM;
        try (PreparedStatement updateGold = connection.prepareStatement(updateGoldSql)) {
            updateGold.setLong(1, request.goldAmount());
            updateGold.setLong(2, request.sessionId());
            updateGold.executeUpdate();
        }
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
        try {
            return readSnapshot(connection, sessionId, SELECT_SESSION_FOR_UPDATE);
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi khoá phiên trade để thi hành: sessionId=" + sessionId, e);
        }
    }

    private static Optional<TradeSessionSnapshot> readSnapshot(Connection connection, long sessionId, String sql)
            throws SQLException {
        long requesterId;
        long targetId;
        TradeStatus status;
        long requesterGold;
        long targetGold;
        boolean requesterConfirmed;
        boolean targetConfirmed;
        try (PreparedStatement select = connection.prepareStatement(sql)) {
            select.setLong(1, sessionId);
            try (ResultSet rs = select.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                requesterId = rs.getLong("requester_character_id");
                targetId = rs.getLong("target_character_id");
                status = TradeStatus.valueOf(rs.getString("status"));
                requesterGold = rs.getLong("requester_gold");
                targetGold = rs.getLong("target_gold");
                requesterConfirmed = rs.getBoolean("requester_confirmed");
                targetConfirmed = rs.getBoolean("target_confirmed");
            }
        }
        ArrayList<TradeOfferItem> items = new ArrayList<TradeOfferItem>();
        try (PreparedStatement select = connection.prepareStatement(SELECT_OFFER_ITEMS)) {
            select.setLong(1, sessionId);
            try (ResultSet rs = select.executeQuery()) {
                while (rs.next()) {
                    items.add(new TradeOfferItem(
                            rs.getLong("id"), rs.getLong("session_id"), rs.getLong("offering_character_id"),
                            rs.getLong("item_id"), rs.getInt("quantity")));
                }
            }
        }
        return Optional.of(new TradeSessionSnapshot(
                sessionId, requesterId, targetId, status, requesterGold, targetGold, requesterConfirmed,
                targetConfirmed, items));
    }

    @Override
    public void setConfirmed(Transaction tx, long sessionId, long characterId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(SET_CONFIRMED)) {
            statement.setLong(1, characterId);
            statement.setLong(2, characterId);
            statement.setLong(3, sessionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đánh dấu confirmed: sessionId=" + sessionId, e);
        }
    }

    @Override
    public void deleteSession(Transaction tx, long sessionId) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try (PreparedStatement statement = connection.prepareStatement(DELETE_SESSION)) {
            statement.setLong(1, sessionId);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi xoá phiên trade: sessionId=" + sessionId, e);
        }
    }

    private static TradeSession mapSession(ResultSet rs) throws SQLException {
        return new TradeSession(
                rs.getLong("id"), rs.getLong("requester_character_id"), rs.getLong("target_character_id"),
                TradeStatus.valueOf(rs.getString("status")), rs.getTimestamp("created_at").toInstant());
    }
}
