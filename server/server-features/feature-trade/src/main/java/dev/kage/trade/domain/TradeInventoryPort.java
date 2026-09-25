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

    /**
     * Sau MỌI lượt chuyển của một giao dịch (cùng transaction), {@code characterId} có đang giữ
     * nhiều dòng hơn sức chứa túi không.
     *
     * <p>Kiểm ở CUỐI chứ không ở từng lượt (thêm 2026-09-24): đổi 1-lấy-1 giữa hai túi đều đầy là
     * hợp lệ, nhưng giữa chừng một bên tạm có thêm một dòng. Trước đây lượt chuyển NGUYÊN DÒNG bỏ
     * qua hẳn kiểm sức chứa, nên túi đủ 20 dòng vẫn nhận thêm thành 21.
     *
     * <p>Mặc định {@code false} CHỈ để các fake port ở test không dính tới giao dịch khỏi phải hiện
     * thực — adapter thật PHẢI ghi đè.
     */
    default boolean exceedsCapacity(Transaction tx, long characterId) {
        return false;
    }

    /**
     * Xếp hàng sau MỌI transaction khác đang ghi túi đồ của CẢ HAI người (cửa hàng, thư, quest,
     * rơi đồ, giao dịch khác...), giữ tới hết {@code tx}. Gọi ngay sau khi khoá phiên giao dịch và
     * TRƯỚC khi đụng vàng/vật phẩm — khoá phiên là ngoại lệ duy nhất được đứng trước, vì chỉ đọc
     * phiên mới biết hai người là ai. Thứ tự giữa hai người do adapter quyết (tất định, không theo
     * vai), xem DATABASE.md §5.1.
     *
     * <p><b>Vì sao</b> (2026-09-25): {@link #exceedsCapacity} đếm dòng ở READ COMMITTED — món một
     * lần mua hàng vừa chèn mà chưa commit thì vô hình, hai bên cùng commit ⇒ túi 21 ô.
     *
     * <p>Mặc định không làm gì CHỈ để fake ở test khỏi phải sửa — adapter thật PHẢI ghi đè.
     */
    default void lockInventories(Transaction tx, long characterIdA, long characterIdB) {
        // Fake trong test: không có transaction thật, không có gì để khoá.
    }

    /** Gói tham số cho {@link #transferItem} — tránh vượt 4 tham số Checkstyle {@code ParameterNumber}. */
    record TransferRequest(long fromCharacterId, long toCharacterId, long itemId, int quantity) {}

    enum TransferOutcome {
        OK,
        /** Không còn đúng chủ (đã bán/gửi/trang bị mất, hoặc item không tồn tại). */
        ITEM_NOT_OWNED,
        /** Còn sở hữu nhưng số lượng hiện tại ít hơn lúc chào. */
        INSUFFICIENT_QUANTITY,
        /** Người nhận đã đầy túi đồ (chỉ xảy ra khi tách stack tạo dòng mới). */
        RECIPIENT_INVENTORY_FULL,
        /**
         * Người đưa đang MẶC món này (sửa 2026-09-24) — feature-trade/CLAUDE.md quyết định 1 vốn
         * ghi "trang bị mất item đã chào giữa chừng ⇒ giao dịch thất bại sạch" nhưng trước đó
         * không nơi nào kiểm.
         */
        ITEM_EQUIPPED
    }
}
