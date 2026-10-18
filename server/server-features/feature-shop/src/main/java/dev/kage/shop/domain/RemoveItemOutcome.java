package dev.kage.shop.domain;

/** Kết quả bớt vật phẩm khỏi túi để bán — xem {@link InventoryGatewayPort#removeUnequippedQuantity}. */
public enum RemoveItemOutcome {
    OK,
    /** Không tồn tại, không thuộc người bán, hoặc không đủ số lượng. */
    INSUFFICIENT_QUANTITY,
    /** Dòng đó đang nằm trong một ô trang bị — phải gỡ ra trước (quyết định chủ dự án 2026-09-24). */
    ITEM_EQUIPPED,
    /**
     * Đủ số lượng, nhưng phần vượt quá chỗ đang chào trong một phiên giao dịch còn mở thì không đủ
     * (dòng 80, chủ dự án duyệt 2026-10-18).
     */
    ITEM_IN_TRADE
}
