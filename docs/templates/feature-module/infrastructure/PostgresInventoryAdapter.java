// TEMPLATE — Adapter: implement Port của domain. NƠI DUY NHẤT biết SQL/PostgreSQL.
package dev.kage.inventory.infrastructure;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.Transaction;
import java.util.Optional;

/**
 * Hiện thực {@link InventoryPort} trên PostgreSQL.
 *
 * <p>SQL chỉ tồn tại ở đây (Điều 24 + [DATABASE §11]). Luôn PreparedStatement,
 * không nối chuỗi (chống SQL injection — [SECURITY §11]). Text block cho SQL dài.
 */
public final class PostgresInventoryAdapter implements InventoryPort {

    // TEMPLATE: SQL dùng text block, tham số hoá bằng '?'
    private static final String FIND_BY_CHARACTER = """
            SELECT i.id, i.item_template_id, i.quantity, i.enhance_level, i.slot_index
              FROM inventory_item i
             WHERE i.character_id = ?
             ORDER BY i.slot_index
            """;

    // TEMPLATE: inject DataSource/connection provider qua constructor.

    @Override
    public Optional<Inventory> findByCharacter(long characterId) {
        // TEMPLATE: PreparedStatement với FIND_BY_CHARACTER, setLong(1, characterId),
        //           map ResultSet → List<ItemStack> → new Inventory(...).
        throw new UnsupportedOperationException("TEMPLATE — điền query thật");
    }

    @Override
    public void save(Transaction tx, Inventory inventory) {
        // TEMPLATE: dùng connection của tx (KHÔNG mở connection riêng — phải cùng transaction).
        throw new UnsupportedOperationException("TEMPLATE — điền upsert thật");
    }
}
