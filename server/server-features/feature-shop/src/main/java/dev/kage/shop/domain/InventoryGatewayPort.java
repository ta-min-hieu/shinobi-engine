package dev.kage.shop.domain;

import java.util.Optional;

/**
 * Cổng thêm/bớt item trong túi đồ ({@code inventory_item}, thuộc feature-inventory) — lý do khai
 * báo riêng ở đây giống hệt {@link GoldPort}: mua/bán cần đổi gold + đổi item NGUYÊN TỬ trong CÙNG
 * transaction, {@code InventoryFacade} không nhận {@code Transaction} chia sẻ giữa 2 feature nên
 * không dùng được. Adapter thật ở {@code server-database} tự viết SQL riêng cho {@code inventory_item}
 * (KHÔNG tái dùng {@code Inventory} aggregate của feature-inventory — {@code Inventory.add()} không
 * nhận {@code Transaction} của feature-shop) — trùng lặp 2 câu SQL nhỏ (đếm số dòng + INSERT/UPDATE)
 * còn rẻ hơn nhiều so với bắc cầu 2 loại {@code Transaction} khác nhau.
 */
public interface InventoryGatewayPort {

    /** {@code Optional.empty()} nếu {@code itemId} không tồn tại hoặc không thuộc {@code characterId}. */
    Optional<OwnedItem> findItem(long characterId, long itemId);

    /**
     * Thêm {@code quantity} item mới (template {@code itemTemplateId}, enhance level 0) vào túi.
     *
     * @return {@code false} nếu túi đầy (không đổi gì) — cùng giới hạn sức chứa PLACEHOLDER với
     *     {@code PostgresInventoryAdapter.DEFAULT_CAPACITY} (GDD chưa chốt sức chứa thật)
     */
    boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity);

    /**
     * Bớt {@code quantity} khỏi item {@code itemId} — xoá hẳn dòng nếu về 0.
     *
     * @return {@code false} nếu item không tồn tại/không đủ số lượng (không đổi gì)
     */
    boolean removeQuantity(Transaction tx, long characterId, long itemId, int quantity);
}
