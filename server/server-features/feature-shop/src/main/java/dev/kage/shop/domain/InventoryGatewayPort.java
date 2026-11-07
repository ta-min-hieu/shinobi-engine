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

    /**
     * Xếp hàng sau MỌI transaction khác đang ghi túi đồ của {@code characterId} (cửa hàng, thư,
     * quest, giao dịch, rơi đồ...), giữ tới hết {@code tx}. PHẢI là lời gọi ĐẦU TIÊN trong
     * transaction, trước khi đụng vàng — thứ tự khoá chung của cả server, xem
     * DATABASE.md §5.1.
     *
     * <p><b>Vì sao</b> (2026-09-25): mỗi đường cấp vật phẩm tự "đếm ô rồi INSERT"; hai đường chạy
     * song song cùng đếm thấy 19 ô ⇒ túi 21 ô.
     *
     * <p>Mặc định không làm gì CHỈ để các fake ở test (không có khái niệm đồng thời) khỏi phải sửa —
     * adapter thật PHẢI ghi đè.
     */
    default void lockInventory(Transaction tx, long characterId) {
        // Fake trong test: không có transaction thật, không có gì để khoá.
    }

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

    /**
     * Như {@link #removeQuantity} nhưng TỪ CHỐI dòng đang mặc — đường bán hàng gọi hàm này.
     *
     * <p>Quyết định chủ dự án 2026-09-24: món đang mặc phải gỡ ra trước mới bán được, cùng luật
     * với giao dịch. Trước đó dòng bị xoá, ô trang bị rơi về NULL ({@code ON DELETE SET NULL}) nhưng
     * World vẫn giữ chỉ số của nó tới lần đăng nhập sau — bán vũ khí lấy vàng mà vẫn giữ ATK.
     *
     * <p>⚠️ Bản mặc định KHÔNG biết gì về trang bị (chỉ để các fake ở test không phải sửa). Adapter
     * thật PHẢI ghi đè và kiểm "đang mặc" SAU khi đã khoá dòng, trong cùng transaction.
     */
    default RemoveItemOutcome removeUnequippedQuantity(Transaction tx, long characterId, long itemId, int quantity) {
        return removeQuantity(tx, characterId, itemId, quantity)
                ? RemoveItemOutcome.OK
                : RemoveItemOutcome.INSUFFICIENT_QUANTITY;
    }

    /**
     * ADR-202 — cấp cường hoá của món {@code itemId} (đọc TRƯỚC khi bớt — bán hết thì dòng biến mất). Mặc định 0 cho
     * các fake ở test; adapter thật đọc dưới khoá túi đồ.
     */
    default int enhanceLevelOf(Transaction tx, long characterId, long itemId) {
        return 0;
    }

    /** ADR-202 — ghi món vừa bán vào danh sách mua lại, cùng transaction bán. Mặc định không ghi (fake ở test). */
    default void recordSale(Transaction tx, SoldItem sale) {
        // fake trong test: không có danh sách mua lại
    }
}
