package dev.kage.mail.domain;

/**
 * Cổng cấp item vào túi đồ — bản sao ý tưởng {@code dev.kage.shop.domain.InventoryGatewayPort},
 * chỉ cần chiều CẤP (nhận thư không có nhánh "trừ item" như bán đồ).
 */
public interface InventoryGatewayPort {

    /** @return {@code true} nếu cấp thành công, {@code false} nếu túi đầy (không đổi gì). */
    boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity);
}
