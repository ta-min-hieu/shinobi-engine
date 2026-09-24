package dev.kage.server.database.mail;

import java.sql.Connection;
import java.util.Comparator;
import java.util.List;

import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.InventoryStackMerge;

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
    // deadlock nhau. enhance_level = 0: xem javadoc #consumeItem. Cột equipped chỉ để chọn THỨ TỰ
    // TRỪ (dòng không mặc trước), KHÔNG đổi thứ tự khoá.
    private static final String SELECT_STACKS_FOR_UPDATE = """
            SELECT id, quantity,
                   EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = inventory_item.id) AS equipped
              FROM inventory_item
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

    /**
     * {@inheritDoc}
     *
     * <p>Gộp chồng TRƯỚC (sửa 2026-09-24): nhận thư là đường cấp vật phẩm THỨ TƯ, cạnh rơi đồ, mua
     * hàng và thưởng quest — bản vá xếp chồng {@code 540adf2} chỉ đếm ba đường nên bỏ sót nó. Hậu
     * quả: mỗi lần nhận thư ăn thêm một ô dù đã có chồng cùng loại còn chỗ, và túi đủ 20 ô thì thư
     * có phi đao bị từ chối {@code INVENTORY_FULL} trong khi mua đúng món đó ở cửa hàng vẫn được.
     */
    @Override
    public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
        Connection connection = ((JdbcTransaction) tx).connection();
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
     * <p><b>Trừ dòng KHÔNG mặc trước</b> (sửa 2026-09-24): thư trừ theo LOẠI chứ không theo dòng.
     * Trước đó nó trừ theo id tăng dần, nên có một thanh kiếm đang mặc (thường có trước, id nhỏ
     * hơn) cộng một thanh dự phòng giống hệt thì thanh ĐANG MẶC bị xoá, ô vũ khí rơi về NULL, còn
     * thanh dự phòng nằm lại trong túi.
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
                rs -> new Stack(rs.getLong("id"), rs.getInt("quantity"), rs.getBoolean("equipped")),
                characterId,
                itemTemplateId);
        List<Stack> consumeOrder =
                stacks.stream().sorted(Comparator.comparing(Stack::equipped)).toList();
        long available = stacks.stream().mapToLong(Stack::quantity).sum();
        if (available < quantity) {
            return false;
        }
        int remaining = quantity;
        for (Stack stack : consumeOrder) {
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

    /** Một dòng {@code inventory_item} đã khoá — các cột cần cho việc trừ. */
    private record Stack(long id, int quantity, boolean equipped) {}
}
