package dev.kage.quest.domain;

/**
 * Cổng cấp item thưởng quest vào túi đồ ({@code inventory_item}, thuộc feature-inventory) — lý do
 * khai báo riêng giống hệt {@link ExpPort}. Nhận {@code itemTemplateCode} (không phải ID số) vì
 * script chỉ biết mã item ổn định (VD {@code "kunai"}) — adapter thật tự tra
 * {@code item_template.id} theo code nội bộ, feature-quest domain không cần biết ID số.
 */
public interface QuestInventoryGrantPort {

    /** {@code false} nếu {@code itemTemplateCode} không tồn tại HOẶC túi đầy (không đổi gì). */
    boolean grantItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity);
}
