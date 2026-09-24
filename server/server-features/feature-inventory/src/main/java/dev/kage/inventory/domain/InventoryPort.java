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
     *
     * @param tx transaction bắt buộc — thao tác có giá trị phải nguyên tử (DATABASE.md §5)
     * @return Inventory ĐÃ LƯU với {@code ItemStack.id} THẬT cho item mới (adapter gán qua
     *     {@code RETURNING id}) — item truyền vào có {@code id == ItemStack.NEW_ITEM_ID} không
     *     mang ID thật, caller cần ID thật (VD báo cho client biết để sau này mặc đồ) phải dùng
     *     giá trị trả về này, không phải tham số {@code inventory} đầu vào.
     */
    Inventory save(Transaction tx, Inventory inventory);

    /**
     * Món {@code itemId} CÒN thuộc {@code characterId}, đọc trong giao dịch {@code tx} và KHOÁ dòng
     * đó tới hết giao dịch — rỗng nếu không tồn tại hoặc thuộc người khác (fail secure, SECURITY.md §4).
     *
     * <p><b>Vì sao cần</b> (2026-09-24): mặc đồ từng xác thực bằng {@link #findByCharacter} — đọc
     * NGOÀI giao dịch, không khoá — trong khi giao dịch/cửa hàng/thư khoá dòng {@code FOR UPDATE} rồi
     * mới kiểm "đang mặc không". Mặc đúng lúc món đó đổi chủ qua giao dịch thì ô trang bị của người
     * cũ trỏ vào đồ của người mới (một thanh kiếm, hai người cùng hưởng chỉ số). Khoá cùng dòng làm
     * hai bên xếp hàng: bên nào tới sau thấy trạng thái SAU khi bên kia commit.
     *
     * <p>Mặc định (fake trong test, không có khái niệm khoá): tra qua {@link #findByCharacter}.
     */
    default Optional<ItemStack> findOwnedItemForUpdate(Transaction tx, long characterId, long itemId) {
        return findByCharacter(characterId).flatMap(inventory -> inventory.findItem(itemId));
    }
}
