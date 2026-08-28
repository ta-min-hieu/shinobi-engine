package dev.kage.mail.domain;

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
     * Thêm {@code quantity} món {@code itemTemplateId} vào túi {@code characterId}.
     *
     * @return {@code true} nếu cấp thành công, {@code false} nếu túi đầy (không đổi gì)
     */
    boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity);

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
