package dev.kage.inventory.domain;

/**
 * Hỏi "món này có đang nằm trong lời chào của một phiên giao dịch còn mở không" — để
 * {@code EnhanceItemUseCase} từ chối cường hoá món đang chào (quyết định chủ dự án 2026-09-29).
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
     * @return {@code true} nếu {@code characterId} đang chào {@code itemId} trong một phiên giao
     *     dịch chưa kết thúc (phiên đã thi hành/huỷ bị xoá hẳn nên không còn lời chào nào)
     */
    boolean isOfferedInActiveTrade(Transaction tx, long characterId, long itemId);
}
