package dev.kage.server.database.shop;

import java.sql.Connection;
import java.util.Optional;

import javax.sql.DataSource;

import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.shop.domain.InventoryGatewayPort;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.RemoveItemOutcome;
import dev.kage.shop.domain.Transaction;

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

    // equipped: equipment_slot.inventory_item_id là cách DUY NHẤT biết một dòng đang mặc (V5). Đọc
    // cùng câu với FOR UPDATE nên phép kiểm nằm SAU khoá dòng, trong cùng transaction.
    private static final String SELECT_FOR_UPDATE = """
            SELECT quantity,
                   EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = inventory_item.id) AS equipped
              FROM inventory_item
             WHERE id = ? AND character_id = ?
               FOR UPDATE
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
        // Gộp chồng TRƯỚC: mua thêm phi đao phải cộng vào chồng đang có, không ăn ô mới (và vì thế
        // cũng không cần kiểm sức chứa). Xem InventoryStackMerge — mua hàng là 1 trong 3 đường cấp
        // vật phẩm, sửa luật xếp chồng ở domain thôi là chưa đủ.
        if (InventoryStackMerge.mergeIntoExistingStack(connection, characterId, itemTemplateId, quantity)) {
            return true;
        }
        int existingCount = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (existingCount >= DEFAULT_CAPACITY) {
            return false;
        }
        // slot_index — nối vào cuối danh sách hiện có.
        JdbcOperations.update(connection, INSERT_ITEM, characterId, itemTemplateId, quantity, existingCount);
        return true;
    }

    @Override
    public boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity) {
        return removeUnequippedQuantity(tx, characterId, itemId, quantity) == RemoveItemOutcome.OK;
    }

    @Override
    public RemoveItemOutcome removeUnequippedQuantity(Transaction tx, long characterId, long itemId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Optional<LockedRow> row = JdbcOperations.queryOne(
                connection,
                SELECT_FOR_UPDATE,
                rs -> new LockedRow(rs.getInt("quantity"), rs.getBoolean("equipped")),
                itemId,
                characterId);
        if (row.isEmpty() || row.get().quantity() < quantity) {
            return RemoveItemOutcome.INSUFFICIENT_QUANTITY;
        }
        if (row.get().equipped()) {
            return RemoveItemOutcome.ITEM_EQUIPPED;
        }
        if (row.get().quantity() == quantity) {
            JdbcOperations.update(connection, DELETE_ITEM, itemId);
        } else {
            JdbcOperations.update(connection, DECREMENT_QUANTITY, quantity, itemId);
        }
        return RemoveItemOutcome.OK;
    }

    /** Dòng {@code inventory_item} đã khoá — các cột cần để quyết có bớt được không. */
    private record LockedRow(int quantity, boolean equipped) {}
}
