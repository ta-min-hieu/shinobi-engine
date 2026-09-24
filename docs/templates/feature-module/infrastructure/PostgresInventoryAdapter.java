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
    // ⚠️ ORDER BY i.id, KHÔNG phải slot_index (2026-09-24): code thật không đọc slot_index —
    // cột không UNIQUE, có thể trùng/có lỗ sau trade/thư/shop/quest (V27 chấp nhận), gateway tự
    // đánh số lại 0..n-1 khi gửi túi đồ. Sắp theo slot_index ở đây là biến cột đó thành có nghĩa
    // và lỗi trùng số thành lỗi hiển thị.
    private static final String FIND_BY_CHARACTER = """
            SELECT i.id, i.item_template_id, i.quantity, i.enhance_level
              FROM inventory_item i
             WHERE i.character_id = ?
             ORDER BY i.id
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
