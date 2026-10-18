package dev.kage.server.database.inventory;

/**
 * Biểu thức SQL "bao nhiêu cái của dòng {@code inventory_item} này đang được chào trong một phiên
 * giao dịch còn mở" — dùng chung cho MỌI đường làm vật phẩm rời túi ngoài giao dịch: cửa hàng, thư,
 * {@code ctx.take_item} của quest, dùng vật phẩm (dòng 80, chủ dự án duyệt 2026-10-18).
 *
 * <p>Trước đó chỉ cường hoá hỏi chuyện này. Bán/gửi thư/nộp quest/uống thuốc vẫn lấy được món đang
 * chào; giao dịch không mất đồ (lúc thi hành nó khoá dòng và kiểm lại số lượng rồi huỷ cả phiên),
 * nhưng người kia chỉ thấy giao dịch thất bại mà không biết vì sao.
 *
 * <p>Đọc thuần, KHÔNG khoá dòng phiên (cùng lý do với {@link PostgresInventoryTradeOfferAdapter}):
 * khoá {@code trade_session} khi đã giữ khoá túi đồ là đảo thứ tự khoá của DATABASE.md §5.1. Không
 * cần: đặt lời chào cũng lấy khoá túi đồ của người chào, nên lúc biểu thức này chạy (sau khoá túi
 * đồ) mọi lời chào của chủ túi hoặc đã commit, hoặc chưa bắt đầu.
 *
 * <p>Hai biến thể theo cách câu gọi gọi tên bảng: {@link #OF_INVENTORY_ITEM} (không bí danh) và
 * {@link #OF_II} (bí danh {@code ii}). Người chào được lọc kèm để một dòng đã đổi chủ (không thể khi
 * phiên còn mở, nhưng rẻ) không bao giờ bị tính nhầm.
 */
public final class TradeOfferedQuantity {

    // Ghép bằng HẰNG SỐ chuỗi (biểu thức hằng lúc biên dịch), không bằng hàm/format: FindSecBugs
    // (SECSQLIJDBC) coi mọi SQL không phải hằng là nghi tiêm SQL, kể cả khi phần ghép là bí danh cố định.
    private static final String HEAD = "COALESCE((SELECT SUM(o.quantity)::int FROM trade_offer_item o"
            + " JOIN trade_session s ON s.id = o.session_id WHERE o.item_id = ";
    private static final String TAIL = " AND s.status = 'OPEN'), 0)";

    /** Biểu thức {@code int} ≥ 0 cho câu chọn từ {@code inventory_item} KHÔNG bí danh. */
    public static final String OF_INVENTORY_ITEM =
            HEAD + "inventory_item.id AND o.offering_character_id = inventory_item.character_id" + TAIL;

    /** Như {@link #OF_INVENTORY_ITEM} cho câu đặt bí danh {@code ii} cho {@code inventory_item}. */
    public static final String OF_II = HEAD + "ii.id AND o.offering_character_id = ii.character_id" + TAIL;

    private TradeOfferedQuantity() {}
}
