package dev.kage.server.gateway;

import dev.kage.protocol.SetTradeOfferCommand;
import dev.kage.protocol.TradeOfferItemInput;

/**
 * Hình dạng hợp lệ của các lệnh kinh tế trên dây — số lượng, vàng, id phải DƯƠNG (rà 2026-10-03).
 *
 * <p>Các use case/value object ({@code BuyItemUseCase}, {@code SellItemUseCase},
 * {@code SetTradeOfferUseCase}, {@code OfferedItemInput}) đã từ chối những giá trị này bằng
 * {@link IllegalArgumentException} — đúng ở tầng đó (fail fast), nhưng exception rơi vào lưới cuối
 * của gateway thành 500 {@code error.server_internal} kèm stack trace ở mức ERROR. Kiểm ở biên dây
 * trả 400 {@code error.invalid_payload}, cùng cách {@link IdempotencyKeyFormat} xử lý khoá sai dạng.
 *
 * <p>Chỉ kiểm HÌNH DẠNG; nghiệp vụ (đủ vàng, còn sở hữu...) vẫn là việc của use case.
 */
final class EconomyCommandShape {

    private EconomyCommandShape() {}

    /** Số lượng mua/bán: 0 hoặc âm không phải một giao dịch. */
    static boolean isValidQuantity(int quantity) {
        return quantity > 0;
    }

    /**
     * Số dòng vật phẩm tối đa của MỘT lời chào (quyết định chủ dự án 2026-10-11). Bằng sức chứa túi đồ
     * (20 ô): mỗi dòng là một ô khác nhau, nên lời chào hợp lệ không thể dài hơn thế. Trước đó không
     * có trần — một khung 1 MiB chở được hàng chục nghìn dòng, và mỗi dòng là một câu SQL trong
     * transaction đang giữ khoá túi đồ.
     */
    static final int MAX_TRADE_OFFER_LINES = 20;

    /**
     * Lời chào: vàng không âm, không quá {@link #MAX_TRADE_OFFER_LINES} dòng, mỗi dòng có id và số
     * lượng dương (khớp {@code OfferedItemInput}).
     */
    static boolean isWellFormed(SetTradeOfferCommand command) {
        if (command.getGoldAmount() < 0 || command.getItemsCount() > MAX_TRADE_OFFER_LINES) {
            return false;
        }
        for (TradeOfferItemInput item : command.getItemsList()) {
            if (item.getItemId() <= 0 || item.getQuantity() <= 0) {
                return false;
            }
        }
        return true;
    }
}
