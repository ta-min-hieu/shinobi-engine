package dev.kage.market.application;

import java.io.Serial;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.market.domain.BuyOrder;
import dev.kage.market.domain.BuyOrderOutcome;
import dev.kage.market.domain.BuyOrderPolicy;
import dev.kage.market.domain.BuyOrderPort;
import dev.kage.market.domain.MarketGoldPort;
import dev.kage.market.domain.MarketInventoryPort;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.market.domain.MarketPolicy;
import dev.kage.market.domain.MarketRecordPort;
import dev.kage.market.domain.NewBuyOrder;
import dev.kage.market.domain.Transaction;
import dev.kage.market.domain.TransactionRunner;

/**
 * Cầu mua (ADR-116). Mỗi lệnh là MỘT transaction; từ chối ném {@link Abort} để rollback trọn. Bán vào lệnh khoá túi HAI
 * người theo {@code character_id} tăng dần (hai người có thể cùng lúc bán vào lệnh của nhau).
 */
public final class BuyOrderUseCase {

    /** Gom port — trần {@code ParameterNumber}. */
    public record Ports(
            BuyOrderPort orders, MarketInventoryPort inventory, MarketGoldPort gold, MarketRecordPort records) {}

    /** Một lần bán vào lệnh vừa chốt — gateway báo người cầu mua nếu online. */
    public record Fill(long orderId, long buyerId, long itemTemplateId, int quantity, long proceeds) {}

    /** Kết quả bán vào lệnh. */
    public record SellResult(BuyOrderOutcome outcome, Optional<Fill> fill) {}

    private final Ports ports;
    private final TransactionRunner tx;
    private final Clock clock;

    public BuyOrderUseCase(Ports ports, TransactionRunner tx, Clock clock) {
        this.ports = ports;
        this.tx = tx;
        this.clock = clock;
    }

    /** Đặt lệnh: trừ ký quỹ ngay. */
    public BuyOrderOutcome place(long buyerId, long itemTemplateId, int quantity, long unitPrice) {
        if (!BuyOrderPolicy.isValidQuantity(quantity)) {
            return BuyOrderOutcome.INVALID_QUANTITY;
        }
        if (!BuyOrderPolicy.isValidUnitPrice(unitPrice)) {
            return BuyOrderOutcome.INVALID_PRICE;
        }
        Instant now = clock.instant();
        long escrow = BuyOrderPolicy.escrowOf(quantity, unitPrice);
        return run(t -> {
            ports.inventory().lock(t, buyerId);
            if (!ports.orders().templateExists(t, itemTemplateId)) {
                throw new Abort(BuyOrderOutcome.UNKNOWN_ITEM);
            }
            if (ports.orders().countActive(t, buyerId) >= BuyOrderPolicy.MAX_ACTIVE_ORDERS) {
                throw new Abort(BuyOrderOutcome.TOO_MANY_ORDERS);
            }
            if (!ports.gold().deduct(t, buyerId, escrow)) {
                throw new Abort(BuyOrderOutcome.INSUFFICIENT_GOLD);
            }
            long id = ports.orders()
                    .insert(
                            t,
                            new NewBuyOrder(
                                    buyerId,
                                    itemTemplateId,
                                    quantity,
                                    unitPrice,
                                    now,
                                    now.plus(BuyOrderPolicy.ORDER_DURATION)));
            ports.records().audit(t, buyerId, "BUY_ORDER_PLACE", detail(id, itemTemplateId, quantity, unitPrice));
        });
    }

    /** Bán {@code quantity} cái từ dòng túi {@code inventoryItemId} vào lệnh {@code orderId}. */
    public SellResult sell(long sellerId, long orderId, long inventoryItemId, int quantity) {
        if (quantity < 1) {
            return new SellResult(BuyOrderOutcome.INVALID_QUANTITY, Optional.empty());
        }
        Instant now = clock.instant();
        AtomicReference<Fill> fill = new AtomicReference<>();
        BuyOrderOutcome outcome = run(t -> {
            // Khoá túi hai bên theo id TĂNG DẦN trước mọi khoá dòng khác (thứ tự chung DATABASE.md §5.1: túi → dòng →
            // vàng). Người mua đọc từ một lần đọc không khoá; đọc lại có khoá ngay sau đó và phải khớp.
            long buyerId = ports.orders().buyerOf(t, orderId).orElseThrow(() -> new Abort(BuyOrderOutcome.NOT_FOUND));
            if (buyerId == sellerId) {
                throw new Abort(BuyOrderOutcome.OWN_ORDER);
            }
            ports.inventory().lock(t, Math.min(sellerId, buyerId));
            ports.inventory().lock(t, Math.max(sellerId, buyerId));
            BuyOrder order = ports.orders()
                    .lockActive(t, orderId, now)
                    .filter(found -> found.buyerId() == buyerId)
                    .orElseThrow(() -> new Abort(BuyOrderOutcome.NOT_FOUND));
            if (quantity > order.remaining()) {
                throw new Abort(BuyOrderOutcome.INVALID_QUANTITY);
            }
            long templateId =
                    switch (ports.inventory().take(t, sellerId, inventoryItemId, quantity)) {
                        case MarketInventoryPort.TakeResult.Taken taken -> taken.itemTemplateId();
                        case MarketInventoryPort.TakeResult.Refused refused -> throw new Abort(of(refused.outcome()));
                    };
            if (templateId != order.itemTemplateId()) {
                throw new Abort(BuyOrderOutcome.WRONG_ITEM);
            }
            if (!ports.inventory().give(t, order.buyerId(), templateId, quantity)) {
                throw new Abort(BuyOrderOutcome.BUYER_BAG_FULL);
            }
            long proceeds = MarketPolicy.proceedsOf(BuyOrderPolicy.escrowOf(quantity, order.unitPrice()));
            ports.gold().credit(t, sellerId, proceeds);
            ports.orders().addFilled(t, orderId, quantity, now);
            String detail = detail(orderId, templateId, quantity, order.unitPrice());
            ports.records().audit(t, sellerId, "BUY_ORDER_SELL", detail);
            ports.records().audit(t, order.buyerId(), "BUY_ORDER_FILL", detail);
            fill.set(new Fill(orderId, order.buyerId(), templateId, quantity, proceeds));
        });
        return new SellResult(outcome, Optional.ofNullable(fill.get()));
    }

    /** Người đặt huỷ — hoàn phần ký quỹ còn lại ngay. */
    public BuyOrderOutcome cancel(long buyerId, long orderId) {
        Instant now = clock.instant();
        return run(t -> {
            ports.inventory().lock(t, buyerId);
            BuyOrder order = ports.orders()
                    .lockActive(t, orderId, now)
                    .filter(found -> found.buyerId() == buyerId)
                    .orElseThrow(() -> new Abort(BuyOrderOutcome.NOT_FOUND));
            ports.gold().credit(t, buyerId, order.escrowRemaining());
            ports.orders().markCancelled(t, orderId, now);
            ports.records()
                    .audit(
                            t,
                            buyerId,
                            "BUY_ORDER_CANCEL",
                            detail(orderId, order.itemTemplateId(), order.remaining(), order.unitPrice()));
        });
    }

    public List<BuyOrder> browse(Optional<Long> itemTemplateId) {
        return ports.orders().browse(itemTemplateId, clock.instant(), MarketPolicy.BROWSE_LIMIT);
    }

    public List<BuyOrder> mine(long buyerId) {
        return ports.orders().mine(buyerId, clock.instant());
    }

    /** Hết hạn: đóng lệnh quá hạn, trả lệnh còn ký quỹ chưa hoàn để gateway gửi thư rồi {@link #markRefunded}. */
    public List<BuyOrder> expireAndListUnrefunded(int limit) {
        ports.orders().expireDue(clock.instant());
        return ports.orders().unrefundedExpired(limit);
    }

    public void markRefunded(long orderId) {
        ports.orders().markRefunded(orderId);
    }

    private static BuyOrderOutcome of(MarketOutcome refused) {
        return switch (refused) {
            case EQUIPPED -> BuyOrderOutcome.EQUIPPED;
            case ENHANCED_NOT_ALLOWED -> BuyOrderOutcome.ENHANCED_NOT_ALLOWED;
            case ITEM_IN_TRADE -> BuyOrderOutcome.ITEM_IN_TRADE;
            case INVALID_QUANTITY -> BuyOrderOutcome.INVALID_QUANTITY;
            default -> BuyOrderOutcome.NOT_FOUND;
        };
    }

    private BuyOrderOutcome run(java.util.function.Consumer<Transaction> work) {
        try {
            tx.inTransaction(work);
            return BuyOrderOutcome.OK;
        } catch (Abort abort) {
            return abort.outcome;
        }
    }

    private static String detail(long orderId, long itemTemplateId, int quantity, long unitPrice) {
        return "{\"orderId\":" + orderId + ",\"itemTemplateId\":" + itemTemplateId + ",\"quantity\":" + quantity
                + ",\"unitPrice\":" + unitPrice + "}";
    }

    /** Từ chối giữa transaction — ném để rollback, không "return sớm" (sẽ commit phần đã làm). */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient BuyOrderOutcome outcome;

        Abort(BuyOrderOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
