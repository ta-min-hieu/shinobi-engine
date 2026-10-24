package dev.kage.server.database.inventory;

import java.sql.Connection;
import java.util.List;

import dev.kage.server.database.JdbcOperations;

/**
 * Tiêu {@code quantity} cái một LOẠI vật phẩm, gom từ nhiều chồng — dùng chung cho đính kèm thư (feature-mail) và
 * nguyên liệu lò đan (ADR-079). Tách từ {@code PostgresMailInventoryGatewayAdapter} 2026-10-24, không đổi hành vi.
 *
 * <p>Chỉ trừ chồng CHƯA cường hoá, không khảm ngọc, không phải linh thú, không đang mặc, và không trừ phần đang chào
 * trong giao dịch còn mở (dòng 80). Đếm ĐỦ trước rồi mới trừ: "không đủ" không đổi gì.
 */
public final class TemplateQuantityConsumer {

    // ORDER BY id: hai transaction gửi thư song song khoá các dòng theo CÙNG thứ tự nên không
    // deadlock nhau. enhance_level = 0: xem javadoc lớp. Cột equipped để LOẠI dòng đang
    // mặc khỏi phần được trừ (vẫn khoá nó như cũ — KHÔNG đổi thứ tự khoá). Cột offered: phần đang chào
    // trong giao dịch còn mở cũng không được trừ (dòng 80) — xem TradeOfferedQuantity.
    private static final String SELECT_STACKS_FOR_UPDATE = """
            SELECT id, quantity,
                   EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = inventory_item.id) AS equipped,
            """ + TradeOfferedQuantity.OF_INVENTORY_ITEM + """
                    AS offered
              FROM inventory_item
             WHERE character_id = ? AND item_template_id = ? AND enhance_level = ?
               AND NOT EXISTS (SELECT 1 FROM item_socket s WHERE s.inventory_item_id = inventory_item.id)
               AND NOT EXISTS (SELECT 1 FROM pet_state p WHERE p.inventory_item_id = inventory_item.id)
             ORDER BY id
               FOR UPDATE
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    private TemplateQuantityConsumer() {}

    public static boolean consume(Connection connection, long characterId, long itemTemplateId, int quantity) {
        return consume(connection, characterId, new ItemLine(itemTemplateId, quantity, 0));
    }

    /** Như trên, chỉ trừ chồng ở đúng cấp cường hoá của {@code line} (thư chở đồ cường hoá — ADR-083). */
    public static boolean consume(Connection connection, long characterId, ItemLine line) {
        long itemTemplateId = line.itemTemplateId();
        int quantity = line.quantity();
        int enhanceLevel = line.enhanceLevel();
        List<Stack> stacks = JdbcOperations.query(
                connection,
                SELECT_STACKS_FOR_UPDATE,
                rs -> new Stack(
                        rs.getLong("id"), rs.getInt("quantity"), rs.getBoolean("equipped"), rs.getInt("offered")),
                characterId,
                itemTemplateId,
                enhanceLevel);
        List<Stack> consumeOrder =
                stacks.stream().filter(stack -> stack.takeable() > 0).toList();
        long available = consumeOrder.stream().mapToLong(Stack::takeable).sum();
        if (available < quantity) {
            return false;
        }
        int remaining = quantity;
        for (Stack stack : consumeOrder) {
            if (remaining == 0) {
                break;
            }
            int taken = Math.min(stack.takeable(), remaining);
            if (taken == stack.quantity()) {
                JdbcOperations.update(connection, DELETE_ITEM, stack.id());
            } else {
                JdbcOperations.update(connection, DECREMENT_QUANTITY, taken, stack.id());
            }
            remaining -= taken;
        }
        return true;
    }

    /** Một dòng {@code inventory_item} đã khoá — các cột cần cho việc trừ. */
    private record Stack(long id, int quantity, boolean equipped, int offered) {

        /** Số cái được phép trừ: dòng đang mặc thì 0, còn lại trừ phần đang chào giao dịch. */
        int takeable() {
            return equipped ? 0 : Math.max(0, quantity - offered);
        }
    }
}
