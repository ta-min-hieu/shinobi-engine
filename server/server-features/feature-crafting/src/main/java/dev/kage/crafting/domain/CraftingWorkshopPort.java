package dev.kage.crafting.domain;

/**
 * Túi đồ, linh thạch và audit của một lần luyện (ADR-079 D3) — mọi lời gọi nằm trong CÙNG một transaction do
 * {@link TransactionRunner} mở. Hiện thực ở server-database (cùng khuôn {@code InventoryGatewayPort} của feature-mail).
 */
public interface CraftingWorkshopPort {

    /** Khoá túi đồ nhân vật tới hết transaction — lời gọi DB đầu tiên (thứ tự khoá chung, DATABASE.md §5.1). */
    void lockInventory(Transaction tx, long characterId);

    /**
     * Tiêu {@code quantity} cái mẫu {@code itemTemplateId} khỏi túi (gom nhiều chồng, không đụng món đang mặc hay
     * phần đang chào trong giao dịch).
     *
     * @return {@code false} nếu không đủ — KHÔNG đổi gì
     */
    boolean consumeMaterial(Transaction tx, long characterId, long itemTemplateId, int quantity);

    /** Trừ linh thạch nếu đủ (qua {@code GoldLedger}). {@code false} = không đủ, không đổi gì. */
    boolean deductGold(Transaction tx, long characterId, long amount);

    /** Cấp món luyện ra. {@code false} = túi không chứa nổi, không đổi gì. */
    boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity);

    void audit(Transaction tx, long characterId, String action, String detailJson);
}
