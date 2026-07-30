package dev.kage.trade.domain;

import java.util.Optional;

/**
 * Cổng đọc/chuyển item — SQL riêng cho {@code inventory_item}, bản sao ý tưởng
 * {@code dev.kage.shop.domain.InventoryGatewayPort}. {@link #transferItem} là bước XÁC THỰC THẬT
 * DUY NHẤT (khoá dòng FOR UPDATE + kiểm sở hữu/số lượng lại) — {@code feature-trade} không có
 * "khoá item lúc chào" nào khác (xem feature-trade/CLAUDE.md).
 */
public interface TradeInventoryPort {

    /** Đọc thuần (không khoá) — dùng để hiển thị lời chào cho client, KHÔNG dùng để xác thực. */
    Optional<OwnedItemSnapshot> findItemById(long itemId);

    /**
     * Chuyển 1 item — khoá dòng FOR UPDATE, xác thực lại CÒN đúng chủ + đủ số lượng, tách stack
     * nếu {@code request.quantity()} nhỏ hơn số lượng hiện có (giữ nguyên dòng cũ, thêm dòng mới
     * cho người nhận — KHÔNG gộp vào stack có sẵn của người nhận, nhất quán với cách hệ thống
     * hiện tại không tự gộp stack ở bất kỳ đường cấp item nào khác).
     */
    TransferOutcome transferItem(Transaction tx, TransferRequest request);

    /** Gói tham số cho {@link #transferItem} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    record TransferRequest(long fromCharacterId, long toCharacterId, long itemId, int quantity) {}

    enum TransferOutcome {
        OK,
        /** Không còn đúng chủ (đã bán/gửi/trang bị mất, hoặc item không tồn tại). */
        ITEM_NOT_OWNED,
        /** Còn sở hữu nhưng số lượng hiện tại ít hơn lúc chào. */
        INSUFFICIENT_QUANTITY,
        /** Người nhận đã đầy túi đồ (chỉ xảy ra khi tách stack tạo dòng mới). */
        RECIPIENT_INVENTORY_FULL
    }
}
