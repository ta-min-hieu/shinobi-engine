package dev.kage.quest.domain;

/**
 * Cổng lấy vật phẩm RA KHỎI túi đồ khi người chơi giao nó cho NPC — mục tiêu quest loại THỨ BA
 * ("mang vật X tới NPC Y", 2026-09-11). Chiều ngược của {@link QuestInventoryGrantPort}, khai báo
 * riêng cùng lý do: {@code inventory_item} thuộc feature-inventory, feature-quest không được import
 * nó, và lần lấy đồ phải nằm TRONG CÙNG transaction với lần ghi tiến độ.
 *
 * <p>Nhận {@code itemTemplateCode} (không phải ID số) vì script chỉ biết mã vật phẩm ổn định —
 * cùng quy ước {@link QuestInventoryGrantPort#grantItemByCode}.
 *
 * <p>⚠️ Chỉ được lấy những gì người chơi không thể coi là "đồ đang dùng": vật KHÔNG đang mặc và
 * CHƯA cường hoá. Một script giao đồ không bao giờ được âm thầm ăn mất thanh kiếm +7 đang cầm trên
 * tay chỉ vì nó trùng mã vật phẩm.
 */
@FunctionalInterface
public interface QuestItemTakePort {

    /**
     * Trừ đúng {@code quantity} vật mã {@code itemTemplateCode} khỏi túi, gom từ bao nhiêu dòng cũng
     * được (túi cũ có thể còn phân mảnh, xem V27).
     *
     * @return {@code false} nếu mã không tồn tại HOẶC không đủ số lượng lấy được — khi đó KHÔNG đổi
     *     gì. Người gọi vẫn phải rollback cả transaction, vì các lần lấy TRƯỚC trong cùng lần chạy
     *     hook có thể đã thành công.
     */
    boolean takeItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity);

    /**
     * Xếp hàng sau MỌI transaction khác đang ghi túi đồ của {@code characterId}, giữ tới hết
     * {@code tx}. PHẢI gọi ĐẦU TIÊN trong transaction có lấy đồ — thứ tự khoá chung, xem
     * DATABASE.md §5.1.
     *
     * <p>Mặc định không làm gì CHỈ để các fake ở test (nhiều cái là lambda) khỏi phải sửa — adapter
     * thật PHẢI ghi đè.
     */
    default void lockInventory(Transaction tx, long characterId) {
        // Fake trong test: không có transaction thật, không có gì để khoá.
    }
}
