package dev.kage.server.database.quest;

import java.sql.Connection;
import java.util.List;

import dev.kage.quest.domain.QuestItemTakePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;

/**
 * Hiện thực {@link QuestItemTakePort} trên PostgreSQL — chiều ngược của
 * {@link PostgresQuestInventoryGrantAdapter}, SQL riêng cho {@code inventory_item} cùng lý do (bắc
 * cầu sang {@code Inventory} aggregate của feature-inventory qua hai loại {@code Transaction} khác
 * nhau tốn hơn vài câu SQL nhỏ).
 *
 * <p><b>Gom từ NHIỀU dòng.</b> Túi tạo trước 2026-09-05 có thể còn phân mảnh (V27 chỉ dồn một lần,
 * và đường {@code INSERT} cũ đã đẻ nhiều dòng cùng loại). Người chơi có ba viên đan nằm ở ba ô vẫn
 * là người có ba viên đan — lấy dòng id nhỏ nhất trước cho tất định, dòng về 0 thì xoá hẳn.
 *
 * <p><b>Khoá trước, đếm sau.</b> {@code FOR UPDATE OF ii} khoá đúng các dòng sắp trừ, nên hai lần
 * lấy đồ song song của cùng người chơi (vd bán ở cửa hàng lúc đang giao) không thể cùng thấy "đủ".
 */
public final class PostgresQuestItemTakeAdapter implements QuestItemTakePort {

    /**
     * Chỉ những dòng người chơi không coi là "đồ đang dùng": KHÔNG đang mặc, CHƯA cường hoá (xem
     * javadoc {@link QuestItemTakePort}). {@code equipment_slot.inventory_item_id} là cách DUY NHẤT
     * biết một dòng đang mặc — V5, dòng đang mặc vẫn nằm trong {@code inventory_item}.
     */
    private static final String LOCK_TAKEABLE_STACKS = """
            SELECT ii.id, ii.quantity
              FROM inventory_item ii
              JOIN item_template it ON it.id = ii.item_template_id
             WHERE ii.character_id = ?
               AND it.code = ?
               AND ii.enhance_level = 0
               AND NOT EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = ii.id)
             ORDER BY ii.id
               FOR UPDATE OF ii
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private record TakeableStack(long id, int quantity) {}

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(((JdbcTransaction) tx).connection(), characterId);
    }

    @Override
    public boolean takeItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        List<TakeableStack> stacks = JdbcOperations.query(
                connection,
                LOCK_TAKEABLE_STACKS,
                rs -> new TakeableStack(rs.getLong("id"), rs.getInt("quantity")),
                characterId,
                itemTemplateCode);
        long available = 0L;
        for (TakeableStack stack : stacks) {
            available += stack.quantity();
        }
        // Mã không tồn tại cũng rơi vào đây (không dòng nào khớp) — và chưa ghi gì cả.
        if (available < quantity) {
            return false;
        }
        int remaining = quantity;
        for (TakeableStack stack : stacks) {
            if (remaining == 0) {
                break;
            }
            if (stack.quantity() <= remaining) {
                JdbcOperations.update(connection, DELETE_ITEM, stack.id());
                remaining -= stack.quantity();
            } else {
                JdbcOperations.update(connection, DECREMENT_QUANTITY, remaining, stack.id());
                remaining = 0;
            }
        }
        return true;
    }
}
