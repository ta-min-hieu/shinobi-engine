package dev.kage.inventory.domain;

import java.util.Optional;

/**
 * Cổng lưu trữ Inventory. Domain định nghĩa hợp đồng; infrastructure hiện thực.
 *
 * <p>KHÔNG có chi tiết công nghệ ở đây (không SQL, không tên bảng).
 */
public interface InventoryPort {

    /**
     * Luôn trả {@code Optional} có giá trị — không có khái niệm "nhân vật không có túi đồ", chỉ có
     * túi đồ RỖNG (0 dòng {@code inventory_item}). {@code characterId} đã được validate từ session
     * trước khi tới đây (Điều 24 chống IDOR), không cần kiểm tra tồn tại ở tầng này.
     */
    Optional<Inventory> findByCharacter(long characterId);

    /**
     * Lưu inventory trong transaction đang mở.
     * @param tx transaction bắt buộc — thao tác có giá trị phải nguyên tử (DATABASE.md §5)
     */
    void save(Transaction tx, Inventory inventory);
}
