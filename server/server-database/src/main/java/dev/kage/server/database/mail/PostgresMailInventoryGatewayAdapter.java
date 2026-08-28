package dev.kage.server.database.mail;

import java.sql.Connection;
import java.util.List;

import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;

/**
 * Hiện thực {@link InventoryGatewayPort} trên PostgreSQL — SQL riêng cho {@code inventory_item},
 * bản sao ý tưởng {@code dev.kage.server.database.shop.PostgresShopInventoryGatewayAdapter}.
 *
 * <p>Khác bản của shop ở CÁCH NHẬN DIỆN món cần trừ: shop bán một dòng túi đồ cụ thể
 * ({@code inventory_item.id}), còn đính kèm thư trên wire chỉ có {@code item_template_id} +
 * {@code quantity} ({@code MailAttachmentView}). Nên {@link #consumeItem} phải gom số lượng từ
 * NHIỀU stack cùng loại.
 */
public final class PostgresMailInventoryGatewayAdapter implements InventoryGatewayPort {

    // PLACEHOLDER trùng PostgresInventoryAdapter.DEFAULT_CAPACITY (GDD chưa chốt sức chứa thật).
    private static final int DEFAULT_CAPACITY = 20;

    private static final String COUNT_ITEMS = """
            SELECT COUNT(*) FROM inventory_item WHERE character_id = ?
            """;

    private static final String INSERT_ITEM = """
            INSERT INTO inventory_item (character_id, item_template_id, quantity, enhance_level, slot_index)
            VALUES (?, ?, ?, 0, ?)
            """;

    // ORDER BY id: hai transaction gửi thư song song khoá các dòng theo CÙNG thứ tự nên không
    // deadlock nhau. enhance_level = 0: xem javadoc #consumeItem.
    private static final String SELECT_STACKS_FOR_UPDATE = """
            SELECT id, quantity FROM inventory_item
             WHERE character_id = ? AND item_template_id = ? AND enhance_level = 0
             ORDER BY id
               FOR UPDATE
            """;

    private static final String DELETE_ITEM = """
            DELETE FROM inventory_item WHERE id = ?
            """;

    private static final String DECREMENT_QUANTITY = """
            UPDATE inventory_item SET quantity = quantity - ? WHERE id = ?
            """;

    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        int existingCount = JdbcOperations.queryOne(connection, COUNT_ITEMS, rs -> rs.getInt(1), characterId)
                .orElse(0);
        if (existingCount >= DEFAULT_CAPACITY) {
            return false;
        }
        // slot_index — nối vào cuối danh sách hiện có.
        JdbcOperations.update(connection, INSERT_ITEM, characterId, itemTemplateId, quantity, existingCount);
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * <p><b>Chỉ trừ từ stack CHƯA cường hoá ({@code enhance_level = 0}).</b> Thư không chở được
     * {@code enhance_level} — {@code mail_item} chỉ có template + số lượng, và người nhận luôn
     * được cấp món ở mức 0. Cho phép ký quỹ một món +7 tức là XOÁ mức cường hoá đó vĩnh viễn mà
     * không báo ai. Chặn ở đây là lựa chọn an toàn hơn (người chơi nhận
     * {@code ATTACHMENT_NOT_OWNED} thay vì mất công cường hoá); mở ra thì phải thêm field vào
     * {@code mail_item} + {@code MailAttachmentView} — đổi schema và đổi protocol, cả hai đều cần
     * chủ dự án quyết (/CLAUDE.md §4).
     *
     * <p>Đếm ĐỦ trước rồi mới trừ: transaction ngoài cùng vẫn rollback nếu trả {@code false},
     * nhưng "không đủ" là kết quả bình thường của nghiệp vụ, không nên để nó phụ thuộc vào việc ai
     * đó nhớ rollback đúng.
     */
    @Override
    public boolean consumeItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
        List<Stack> stacks = JdbcOperations.query(
                connection,
                SELECT_STACKS_FOR_UPDATE,
                rs -> new Stack(rs.getLong("id"), rs.getInt("quantity")),
                characterId,
                itemTemplateId);
        long available = stacks.stream().mapToLong(Stack::quantity).sum();
        if (available < quantity) {
            return false;
        }
        int remaining = quantity;
        for (Stack stack : stacks) {
            if (remaining == 0) {
                break;
            }
            int taken = Math.min(stack.quantity(), remaining);
            if (taken == stack.quantity()) {
                JdbcOperations.update(connection, DELETE_ITEM, stack.id());
            } else {
                JdbcOperations.update(connection, DECREMENT_QUANTITY, taken, stack.id());
            }
            remaining -= taken;
        }
        return true;
    }

    /** Một dòng {@code inventory_item} đã khoá — chỉ hai cột cần cho việc trừ. */
    private record Stack(long id, int quantity) {}
}
