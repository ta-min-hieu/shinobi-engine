package dev.kage.inventory.domain;

import java.util.Optional;

/**
 * Cổng lưu trữ {@link Equipment}. Domain định nghĩa hợp đồng; infrastructure hiện thực — cùng
 * pattern {@link InventoryPort}.
 */
public interface EquipmentPort {

    /**
     * Luôn trả {@code Optional} có giá trị — không có khái niệm "nhân vật không có trang bị", chỉ
     * có trang bị RỖNG (0 dòng {@code equipment_slot}). {@code characterId} đã validate từ session
     * trước khi tới đây (Điều 24 chống IDOR).
     */
    Optional<Equipment> findByCharacter(long characterId);

    /**
     * Lưu equipment trong transaction đang mở.
     * @param tx transaction bắt buộc — thao tác có giá trị phải nguyên tử (DATABASE.md §5)
     */
    void save(Transaction tx, Equipment equipment);
}
