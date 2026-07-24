package dev.kage.server.database.trade;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.trade.domain.OwnedItemSnapshot;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeInventoryPort;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link TradeInventoryPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * bản sao ý tưởng {@code PostgresShopInventoryGatewayAdapter}. {@link #transferItem} là bước XÁC
 * THỰC THẬT DUY NHẤT của toàn bộ feature-trade (xem feature-trade/CLAUDE.md) — khoá dòng FOR
 * UPDATE, kiểm còn đúng chủ + đủ số lượng, tách stack nếu cần.
 */
public final class PostgresTradeInventoryAdapter implements TradeInventoryPort {

    // PLACEHOLDER trùng PostgresInventoryAdapter.DEFAULT_CAPACITY (GDD chưa chốt sức chứa thật).
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_BY_ID = """
            SELECT id, character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ?
            """;

    private static final String SELECT_FOR_UPDATE = """
            SELECT character_id, item_template_id, quantity, enhance_level FROM inventory_item WHERE id = ? FOR UPDATE
            """;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String UPDATE_OWNER = """
            UPDATE inventory_item SET character_id = ? WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private static final String INSERT_ROW = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, ?, ?)
            """;

    private final DataSource dataSource;

    public PostgresTradeInventoryAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<OwnedItemSnapshot> findItemById(long itemId) {
        try (var connection = dataSource.getConnection();
                var statement = connection.prepareStatement(FIND_BY_ID)) {
            statement.setLong(1, itemId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi tra item: itemId=" + itemId, e);
        }
    }

    @Override
    public TransferOutcome transferItem(Transaction tx, TransferRequest request) {
        var connection = ((JdbcTransaction) tx).connection();
        try {
            var current = selectForUpdate(connection, request.itemId());
            if (current.isEmpty() || current.get().characterId() != request.fromCharacterId()) {
                return TransferOutcome.ITEM_NOT_OWNED;
            }
            if (current.get().quantity() < request.quantity()) {
                return TransferOutcome.INSUFFICIENT_QUANTITY;
            }
            boolean isWholeRow = current.get().quantity() == request.quantity();
            if (!isWholeRow && countItems(connection, request.toCharacterId()) >= DEFAULT_CAPACITY) {
                return TransferOutcome.RECIPIENT_INVENTORY_FULL;
            }
            if (isWholeRow) {
                updateOwner(connection, request.itemId(), request.toCharacterId());
            } else {
                decrementQuantity(connection, request.itemId(), request.quantity());
                insertRow(connection, request, current.get());
            }
            return TransferOutcome.OK;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi chuyển item: itemId=" + request.itemId(), e);
        }
    }

    private static Optional<OwnedItemSnapshot> selectForUpdate(Connection connection, long itemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_FOR_UPDATE)) {
            statement.setLong(1, itemId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OwnedItemSnapshot(
                        itemId, rs.getLong("character_id"), rs.getLong("item_template_id"), rs.getInt("quantity"),
                        rs.getInt("enhance_level")));
            }
        }
    }

    private static int countItems(Connection connection, long characterId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(COUNT_ITEMS)) {
            statement.setLong(1, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private static void updateOwner(Connection connection, long itemId, long newOwnerCharacterId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(UPDATE_OWNER)) {
            statement.setLong(1, newOwnerCharacterId);
            statement.setLong(2, itemId);
            statement.executeUpdate();
        }
    }

    private static void decrementQuantity(Connection connection, long itemId, int quantity) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DECREMENT_QUANTITY)) {
            statement.setInt(1, quantity);
            statement.setLong(2, itemId);
            statement.executeUpdate();
        }
    }

    private static void insertRow(Connection connection, TransferRequest request, OwnedItemSnapshot original)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_ROW)) {
            statement.setLong(1, request.toCharacterId());
            statement.setLong(2, original.itemTemplateId());
            statement.setInt(3, request.quantity());
            statement.setInt(4, original.enhanceLevel());
            statement.setInt(5, countItems(connection, request.toCharacterId())); // nối vào cuối danh sách hiện có
            statement.executeUpdate();
        }
    }

    private static OwnedItemSnapshot mapRow(ResultSet rs) throws SQLException {
        return new OwnedItemSnapshot(
                rs.getLong("id"), rs.getLong("character_id"), rs.getLong("item_template_id"), rs.getInt("quantity"),
                rs.getInt("enhance_level"));
    }
}
