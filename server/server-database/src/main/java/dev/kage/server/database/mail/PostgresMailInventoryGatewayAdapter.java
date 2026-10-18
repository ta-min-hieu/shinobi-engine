package dev.kage.server.database.mail;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.kage.mail.domain.InventoryGatewayPort;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.Transaction;
import dev.kage.server.database.JdbcOperations;
import dev.kage.server.database.inventory.CharacterInventoryLock;
import dev.kage.server.database.inventory.InventoryStackMerge;
import dev.kage.server.database.inventory.TradeOfferedQuantity;

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

    // ORDER BY id: hai transaction gửi thư song song khoá các dòng theo CÙNG thứ tự nên không
    // deadlock nhau. enhance_level = 0: xem javadoc #consumeItem. Cột equipped để LOẠI dòng đang
    // mặc khỏi phần được trừ (vẫn khoá nó như cũ — KHÔNG đổi thứ tự khoá). Cột offered: phần đang chào
    // trong giao dịch còn mở cũng không được trừ (dòng 80) — xem TradeOfferedQuantity.
    private static final String SELECT_STACKS_FOR_UPDATE = """
            SELECT id, quantity,
                   EXISTS (SELECT 1 FROM equipment_slot es WHERE es.inventory_item_id = inventory_item.id) AS equipped,
            """ + TradeOfferedQuantity.OF_INVENTORY_ITEM + """
                    AS offered
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

    @Override
    public void lockInventory(Transaction tx, long characterId) {
        CharacterInventoryLock.acquire(((JdbcTransaction) tx).connection(), characterId);
    }

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
        // Không gộp được ⇒ mở ô mới, tách theo max_stack: thư gom số lượng từ NHIỀU dòng của người
        // gửi (3 thanh kiếm = 3 dòng), nên số tới đây có thể vượt max_stack của loại.
        return InventoryStackMerge.insertRespectingMaxStack(connection, characterId, itemTemplateId, quantity);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Hai lượt. Lượt một thử gộp TỪNG dòng vào chồng sẵn có, đúng thứ tự — y hệt cách cấp từng
     * dòng trước đây chọn chồng (chồng sẵn có luôn có id nhỏ hơn mọi ô mới mở), nên không lần nhận
     * thư nào tốn nhiều ô hơn trước. Lượt hai dồn các dòng KHÔNG gộp được theo loại rồi mới mở ô:
     * {@code ceil(tổng / max_stack)} là số ô ít nhất chứa nổi phần đó.
     *
     * <p>Tổng dồn bằng {@code long}: thư hệ thống do server dựng không bị ràng bởi túi người gửi, nên
     * không giả định tổng vừa {@code int}; vượt thì chắc chắn không túi nào chứa nổi.
     */
    @Override
    public boolean grantItems(Transaction tx, long characterId, List<MailAttachment> attachments) {
        Connection connection = ((JdbcTransaction) tx).connection();
        Map<Long, Long> unmergedByTemplate = new LinkedHashMap<>();
        for (MailAttachment attachment : attachments) {
            boolean merged = InventoryStackMerge.mergeIntoExistingStack(
                    connection, characterId, attachment.itemTemplateId(), attachment.quantity());
            if (!merged) {
                unmergedByTemplate.merge(attachment.itemTemplateId(), (long) attachment.quantity(), Long::sum);
            }
        }
        for (Map.Entry<Long, Long> unmerged : unmergedByTemplate.entrySet()) {
            long quantity = unmerged.getValue();
            if (quantity > Integer.MAX_VALUE
                    || !InventoryStackMerge.insertRespectingMaxStack(
                            connection, characterId, unmerged.getKey(), Math.toIntExact(quantity))) {
                return false;
            }
        }
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
     * <p><b>Không bao giờ trừ dòng ĐANG MẶC</b> (sửa 2026-09-24, hai bước): thư trừ theo LOẠI chứ
     * không theo dòng. Bản cũ trừ theo id tăng dần, nên thanh kiếm đang mặc (thường có trước, id
     * nhỏ hơn) bị xoá thay cho thanh dự phòng giống hệt. Bước sau (quyết định chủ dự án cùng
     * ngày): dòng đang mặc KHÔNG tính vào số có thể gửi — chỉ còn thanh đang mặc thì từ chối
     * ({@code ATTACHMENT_NOT_OWNED}), cùng luật với bán hàng và giao dịch. Lấy nó đi thì ô trang bị
     * rơi về NULL mà World vẫn giữ chỉ số tới lần đăng nhập sau.
     *
     * <p><b>Không trừ phần ĐANG CHÀO trong giao dịch</b> (dòng 80, chủ dự án duyệt 2026-10-18): chồng 10
     * phi đao chào 4 thì gửi thư được tối đa 6. Trước đó thư lấy luôn phần đang chào và giao dịch
     * thất bại lúc xác nhận mà người kia không biết vì sao. Thiếu vì lý do này vẫn là
     * {@code ATTACHMENT_NOT_OWNED} — cùng cách dòng đang mặc được đối xử.
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
                rs -> new Stack(
                        rs.getLong("id"), rs.getInt("quantity"), rs.getBoolean("equipped"), rs.getInt("offered")),
                characterId,
                itemTemplateId);
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
