package dev.kage.market.api;

import java.util.List;
import java.util.Optional;

import dev.kage.market.application.BuyOrderUseCase;
import dev.kage.market.domain.BuyOrder;
import dev.kage.market.domain.BuyOrderOutcome;

/** API cầu mua (ADR-116). Chạm DB — chỉ gọi trên Virtual Thread; {@code characterId} từ session. */
public final class BuyOrderFacade {

    /** Composition không có cầu mua (test) — không lệnh nào, mọi lệnh ghi bị từ chối. */
    public static final BuyOrderFacade DISABLED = new BuyOrderFacade(Optional.empty());

    private final Optional<BuyOrderUseCase> useCase;

    /** Dựng ở composition root. */
    public BuyOrderFacade(BuyOrderUseCase useCase) {
        this(Optional.of(useCase));
    }

    private BuyOrderFacade(Optional<BuyOrderUseCase> useCase) {
        this.useCase = useCase;
    }

    /** Đặt lệnh, trừ ký quỹ ngay. */
    public BuyOrderOutcome place(long buyerId, long itemTemplateId, int quantity, long unitPrice) {
        return useCase.map(u -> u.place(buyerId, itemTemplateId, quantity, unitPrice))
                .orElse(BuyOrderOutcome.NOT_FOUND);
    }

    /** Bán vào lệnh từ một dòng túi của chính mình. */
    public BuyOrderUseCase.SellResult sell(long sellerId, long orderId, long inventoryItemId, int quantity) {
        return useCase.map(u -> u.sell(sellerId, orderId, inventoryItemId, quantity))
                .orElse(new BuyOrderUseCase.SellResult(BuyOrderOutcome.NOT_FOUND, Optional.empty()));
    }

    /** Người đặt huỷ — hoàn ký quỹ còn lại. */
    public BuyOrderOutcome cancel(long buyerId, long orderId) {
        return useCase.map(u -> u.cancel(buyerId, orderId)).orElse(BuyOrderOutcome.NOT_FOUND);
    }

    /** Lệnh đang mở, giá cao trước. */
    public List<BuyOrder> browse(Optional<Long> itemTemplateId) {
        return useCase.map(u -> u.browse(itemTemplateId)).orElse(List.of());
    }

    /** Lệnh đang mở của chính mình. */
    public List<BuyOrder> mine(long buyerId) {
        return useCase.map(u -> u.mine(buyerId)).orElse(List.of());
    }

    /** Đóng lệnh quá hạn, trả lệnh còn ký quỹ chưa hoàn. */
    public List<BuyOrder> expireAndListUnrefunded(int limit) {
        return useCase.map(u -> u.expireAndListUnrefunded(limit)).orElse(List.of());
    }

    /** Đánh dấu đã hoàn ký quỹ (sau khi gửi thư). */
    public void markRefunded(long orderId) {
        useCase.ifPresent(u -> u.markRefunded(orderId));
    }
}
