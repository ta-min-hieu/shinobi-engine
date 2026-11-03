package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.util.List;

import dev.kage.server.database.JdbcOperations;

/**
 * Lấy {@code quantity} cái theo mã mẫu khỏi túi một nhân vật — gom từ nhiều dòng, chỉ dòng không đang mặc,
 * chưa cường hoá, trừ phần đang chào giao dịch. Dùng chung cho giao đồ nhiệm vụ và Tẩy Căn Đan (ADR-066).
 * Người gọi PHẢI đã giữ {@link CharacterInventoryLock} trong cùng transaction.
 *
 * <p><b>Gom từ NHIỀU dòng.</b> Túi tạo trước 2026-09-05 có thể còn phân mảnh (V27 chỉ dồn một lần,
 * và đường {@code INSERT} cũ đã đẻ nhiều dòng cùng loại). Người chơi có ba viên đan nằm ở ba ô vẫn
 * là người có ba viên đan — lấy dòng id nhỏ nhất trước cho tất định, dòng về 0 thì xoá hẳn.
 *
 * <p><b>Khoá trước, đếm sau.</b> {@code FOR UPDATE OF ii} khoá đúng các dòng sắp trừ, nên hai lần
 * lấy đồ song song của cùng người chơi (vd bán ở cửa hàng lúc đang giao) không thể cùng thấy "đủ".
 */
public final class InventoryItemTake {

    /**
     * Chỉ những dòng người chơi không coi là "đồ đang dùng": KHÔNG đang mặc, CHƯA cường hoá (xem
     * javadoc {@code QuestItemTakePort}). {@code equipment_slot.inventory_item_id} là cách DUY NHẤT
     * biết một dòng đang mặc — V5, dòng đang mặc vẫn nằm trong {@code inventory_item}. Cột
     * {@code offered}: phần đang chào trong giao dịch còn mở cũng KHÔNG lấy được (dòng 80, chủ dự án
     * duyệt 2026-10-18) — xem {@link TradeOfferedQuantity}.
     */
    private static final String LOCK_TAKEABLE_STACKS =
            "SELECT ii.id, ii.quantity, " + TradeOfferedQuantity.OF_II + " AS offered" + """

              FROM inventory_item ii
              JOIN item_template it ON it.id = ii.item_template_id
             WHERE ii.character_id = ?
               AND it.code = ?
               AND ii.enhance_level = 0
               AND (NOT ii.locked OR ?)
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

    /** {@code takeable}: số cái lấy được — tổng dòng trừ phần đang chào giao dịch. */
    private record TakeableStack(long id, int quantity, int takeable) {}

    private InventoryItemTake() {}

    /**
     * {@code false} = không đủ (chưa ghi gì). Mã không tồn tại cũng là {@code false}. Dùng trực tiếp — lấy cả chồng
     * khoá (ADR-159 D3).
     */
    public static boolean takeByCode(Connection connection, long characterId, String itemTemplateCode, int quantity) {
        return take(connection, characterId, new Want(itemTemplateCode, quantity, true));
    }

    /** ADR-159 — nộp vật phẩm (nhiệm vụ): KHÔNG lấy chồng người chơi đã khoá. */
    public static boolean takeUnlockedByCode(
            Connection connection, long characterId, String itemTemplateCode, int quantity) {
        return take(connection, characterId, new Want(itemTemplateCode, quantity, false));
    }

    /** Lấy bao nhiêu cái mã nào, có được lấy chồng đã khoá không (ADR-159). */
    private record Want(String itemTemplateCode, int quantity, boolean includeLocked) {}

    private static boolean take(Connection connection, long characterId, Want want) {
        String itemTemplateCode = want.itemTemplateCode();
        int quantity = want.quantity();
        boolean includeLocked = want.includeLocked();
        List<TakeableStack> stacks = JdbcOperations.query(
                connection,
                LOCK_TAKEABLE_STACKS,
                rs -> new TakeableStack(
                        rs.getLong("id"),
                        rs.getInt("quantity"),
                        Math.max(0, rs.getInt("quantity") - rs.getInt("offered"))),
                characterId,
                itemTemplateCode,
                includeLocked);
        long available = 0L;
        for (TakeableStack stack : stacks) {
            available += stack.takeable();
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
            int taken = Math.min(stack.takeable(), remaining);
            if (taken == 0) {
                continue;
            }
            if (taken == stack.quantity()) {
                JdbcOperations.update(connection, DELETE_ITEM, stack.id());
            } else {
                JdbcOperations.update(connection, DECREMENT_QUANTITY, taken, stack.id());
            }
            remaining -= taken;
        }
        return true;
    }
}
