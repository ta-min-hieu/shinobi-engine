package dev.kage.server.database.shop;

import dev.kage.server.database.DatabaseAccessException;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.Transaction;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import javax.sql.DataSource;

/**
 * Hiện thực {@link InventoryGatewayPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * KHÔNG tái dùng {@code Inventory} aggregate của feature-inventory (xem javadoc
 * {@link InventoryGatewayPort} — 2 loại {@code Transaction} khác nhau, bắc cầu tốn hơn trùng lặp
 * 2 câu SQL nhỏ này).
 */
public final class PostgresShopInventoryGatewayAdapter implements InventoryGatewayPort {

    // PLACEHOLDER trùng với PostgresInventoryAdapter.DEFAULT_CAPACITY (GDD chưa chốt sức chứa
    // thật) — đổi thì phải đổi cả 2 chỗ cho tới khi có nguồn chung (VD cột character.inventory_capacity).
    private static final int DEFAULT_CAPACITY = 20;

    private static final String FIND_ITEM = """
            SELECT id, item_template_id, quantity FROM inventory_item WHERE id = ? AND character_id = ?
            """;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, ?)
            """;

    private static final String SELECT_QUANTITY_FOR_UPDATE = """
            SELECT quantity FROM inventory_item WHERE id = ? AND character_id = ? FOR UPDATE
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private final DataSource dataSource;

    public PostgresShopInventoryGatewayAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public Optional<OwnedItem> findItem(long characterId, long itemId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(FIND_ITEM)) {
            statement.setLong(1, itemId);
            statement.setLong(2, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                return Optional.of(new OwnedItem(
                        rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity")));
            }
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi đọc inventory_item: itemId=" + itemId, e);
        }
    }

    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try {
            int existingCount = countItems(connection, characterId);
            if (existingCount >= DEFAULT_CAPACITY) {
                return false;
            }
            try (PreparedStatement statement = connection.prepareStatement(INSERT_ITEM)) {
                statement.setLong(1, characterId);
                statement.setLong(2, itemTemplateId);
                statement.setInt(3, quantity);
                statement.setInt(4, existingCount); // slot_index — nối vào cuối danh sách hiện có
                statement.executeUpdate();
            }
            return true;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi cộng item mua: characterId=" + characterId, e);
        }
    }

    @Override
    public boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        try {
            int currentQuantity = selectQuantityForUpdate(connection, characterId, itemId);
            if (currentQuantity < quantity) {
                return false;
            }
            if (currentQuantity == quantity) {
                deleteItem(connection, itemId);
            } else {
                decrementQuantity(connection, itemId, quantity);
            }
            return true;
        } catch (SQLException e) {
            throw new DatabaseAccessException("Lỗi bớt item bán: itemId=" + itemId, e);
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

    /** {@code -1} nếu không tồn tại/không thuộc {@code characterId} — coi như "không đủ số lượng". */
    private static int selectQuantityForUpdate(Connection connection, long characterId, long itemId)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(SELECT_QUANTITY_FOR_UPDATE)) {
            statement.setLong(1, itemId);
            statement.setLong(2, characterId);
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() ? rs.getInt(1) : -1;
            }
        }
    }

    private static void deleteItem(Connection connection, long itemId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(DELETE_ITEM)) {
            statement.setLong(1, itemId);
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
}
