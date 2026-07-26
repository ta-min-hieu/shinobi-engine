package dev.kage.server.database.shop;

import dev.kage.server.database.JdbcOperations;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.Transaction;
import java.sql.Connection;
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
        return JdbcOperations.queryOne(
                dataSource,
                FIND_ITEM,
                rs -> new OwnedItem(rs.getLong("id"), rs.getLong("item_template_id"), rs.getInt("quantity")),
                itemId,
                characterId);
    }

    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int existingCount =
                JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId).orElse(0);
        if (existingCount >= DEFAULT_CAPACITY) {
            return false;
        }
        // slot_index — nối vào cuối danh sách hiện có.
        JdbcOperations.update(connection, INSERT_ITEM, characterId, itemTemplateId, quantity, existingCount);
        return true;
    }

    @Override
    public boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int currentQuantity = selectQuantityForUpdate(connection, characterId, itemId);
        if (currentQuantity < quantity) {
            return false;
        }
        if (currentQuantity == quantity) {
            JdbcOperations.update(connection, DELETE_ITEM, itemId);
        } else {
            JdbcOperations.update(connection, DECREMENT_QUANTITY, quantity, itemId);
        }
        return true;
    }

    /** {@code -1} nếu không tồn tại/không thuộc {@code characterId} — coi như "không đủ số lượng". */
    private static int selectQuantityForUpdate(Connection connection, long characterId, long itemId) {
        return JdbcOperations.queryOne(connection, SELECT_QUANTITY_FOR_UPDATE, rs -> rs.getInt(1), itemId, characterId)
                .orElse(-1);
    }
}
