package dev.kage.mail.domain;

import java.util.List;

/**
 * Cổng đổi túi đồ — bản sao ý tưởng {@code dev.kage.shop.domain.InventoryGatewayPort}, đủ hai
 * chiều: {@link #consumeItem} ký quỹ món đính kèm khỏi túi người GỬI lúc gửi, {@link #grantItem}
 * trả nó vào túi người NHẬN lúc nhận.
 *
 * <p>Thiếu chiều trừ thì gửi cùng một món cho 10 người là nhân bản nó 10 lần — thư không "giữ" gì
 * cả, nó chỉ ghi lại con số rồi {@code ClaimMailUseCase} cấp item thật.
 */
public interface InventoryGatewayPort {

    /**
     * Xếp hàng sau MỌI transaction khác đang ghi túi đồ của {@code characterId} (cửa hàng, thư,
     * quest, giao dịch, rơi đồ...), giữ tới hết {@code tx}. PHẢI là lời gọi ĐẦU TIÊN trong
     * transaction, trước khi khoá dòng thư hay đụng vàng — thứ tự khoá chung của cả server, xem
     * DATABASE.md §5.1.
     *
     * <p>Mặc định không làm gì CHỈ để các fake ở test khỏi phải sửa — adapter thật PHẢI ghi đè.
     */
    default void lockInventory(Transaction tx, long characterId) {
        // Fake trong test: không có transaction thật, không có gì để khoá.
    }

    /**
     * Thêm {@code quantity} món {@code itemTemplateId} vào túi {@code characterId}.
     *
     * @return {@code true} nếu cấp thành công, {@code false} nếu túi đầy (không đổi gì)
     */
    boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity);

    /**
     * Cấp TRỌN lô đính kèm của một lá thư, dùng ÍT Ô NHẤT có thể: dòng nào gộp trọn được vào một
     * chồng sẵn có thì gộp, phần còn lại của CÙNG một loại được dồn lại rồi mới mở ô (tách theo
     * {@code max_stack}).
     *
     * <p><b>Vì sao không gọi {@link #grantItem} từng dòng</b> (lỗi thật 2026-10-10). Luật gộp chỉ
     * nhận khi CẢ dòng vừa một chồng, nên 21 dòng "50 phi đao" mở 21 ô (50 + 50 > 99) dù cả lô chỉ
     * là 1050 cái = 11 ô. Thư đó KHÔNG túi nào nhận nổi, kể cả túi rỗng, mà thư còn đồ thì không
     * xoá được — vật phẩm và vàng đi kèm kẹt vĩnh viễn. Dồn theo loại thì lô nào người gửi từng giữ
     * được trong túi cũng vừa một túi rỗng.
     *
     * <p>Mặc định cấp từng dòng CHỈ để các fake ở test khỏi phải sửa — adapter thật PHẢI ghi đè.
     *
     * @return {@code false} nếu túi không đủ ô — CÓ THỂ đã ghi dở, người gọi phải rollback
     */
    default boolean grantItems(Transaction tx, long characterId, List<MailAttachment> attachments) {
        for (MailAttachment attachment : attachments) {
            if (!grantItem(tx, characterId, attachment.itemTemplateId(), attachment.quantity())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Trừ {@code quantity} món {@code itemTemplateId} khỏi túi {@code characterId} — KHOÁ dòng
     * trước khi đọc số lượng, để hai lá thư gửi song song không cùng "thấy đủ" trên một stack.
     *
     * <p>Đính kèm nhận diện món bằng {@code itemTemplateId} (không phải {@code inventory_item.id})
     * vì {@code MailAttachmentView} trên wire chỉ chở template + số lượng — nên số lượng có thể
     * nằm rải trên nhiều stack và bị gom lại từ chúng.
     *
     * @return {@code true} nếu trừ đủ, {@code false} nếu không đủ/không có món đó (KHÔNG đổi gì —
     *     kể cả khi {@code itemTemplateId} không tồn tại trong {@code item_template})
     */
    boolean consumeItem(Transaction tx, long characterId, long itemTemplateId, int quantity);
}
