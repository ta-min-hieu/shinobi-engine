package dev.kage.inventory.domain;

/**
 * Hỏi "món này có đang nằm trong lời chào của một phiên giao dịch còn mở không" — để
 * {@code EnhanceItemUseCase} từ chối cường hoá món đang chào (quyết định chủ dự án 2026-09-29), và
 * {@code UseItemUseCase} không cho dùng phần đang chào (dòng 80, chủ dự án duyệt 2026-10-18).
 *
 * <p>Bản khai riêng của feature-inventory, KHÔNG phụ thuộc Gradle vào feature-trade — cùng cách
 * feature-trade tự khai {@code TradeInventoryPort} để đọc {@code inventory_item}: adapter thật ở
 * {@code server-database} đọc bảng của phiên giao dịch.
 *
 * <p>PHẢI gọi TRONG transaction, SAU khoá túi đồ ({@link InventoryPort#findByCharacterForUpdate}):
 * đặt lời chào cũng lấy khoá túi đồ của người chào, nên hai bên xếp hàng — hoặc cường hoá commit
 * trước (lời chào hiện cấp mới), hoặc lời chào commit trước (cường hoá thấy nó và từ chối). Hỏi
 * ngoài khoá thì lời chào có thể commit chen giữa lúc hỏi và lúc cường hoá commit.
 */
public interface TradeOfferPort {

    /**
     * Số cái của dòng {@code itemId} mà {@code characterId} đang chào trong các phiên giao dịch
     * chưa kết thúc (phiên đã thi hành/huỷ bị xoá hẳn nên không còn lời chào nào).
     *
     * <p>Trả SỐ LƯỢNG chứ không chỉ có/không (dòng 80, 2026-10-18): chào 1 phi đao trong chồng 99
     * thì 98 cái còn lại vẫn dùng được — chỉ phần đang chào bị giữ.
     *
     * @return {@code 0} nếu dòng đó không nằm trong lời chào nào
     */
    int offeredQuantity(Transaction tx, long characterId, long itemId);

    /** {@code true} nếu ít nhất một cái của dòng {@code itemId} đang được chào — xem {@link #offeredQuantity}. */
    default boolean isOfferedInActiveTrade(Transaction tx, long characterId, long itemId) {
        return offeredQuantity(tx, characterId, itemId) > 0;
    }
}
