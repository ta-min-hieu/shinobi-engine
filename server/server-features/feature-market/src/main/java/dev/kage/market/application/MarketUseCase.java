package dev.kage.market.application;

import java.io.Serial;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.market.domain.ListingDraft;
import dev.kage.market.domain.ListingRequest;
import dev.kage.market.domain.MarketGoldPort;
import dev.kage.market.domain.MarketInventoryPort;
import dev.kage.market.domain.MarketListing;
import dev.kage.market.domain.MarketOutcome;
import dev.kage.market.domain.MarketPolicy;
import dev.kage.market.domain.MarketPort;
import dev.kage.market.domain.MarketRecordPort;
import dev.kage.market.domain.Transaction;
import dev.kage.market.domain.TransactionRunner;

/**
 * Treo / mua / huỷ / hết hạn (ADR-063). Mỗi lệnh là MỘT transaction; thứ tự khoá chung: túi đồ → dòng
 * treo → vàng (DATABASE.md §5.1). Nhánh từ chối ném {@link Abort} để rollback trọn — kể cả khoá chống
 * trùng đã ghi (bấm lại sau khi sửa lỗi vẫn chạy được, cùng luật cường hoá).
 */
public final class MarketUseCase {

    /** Gom port — trần {@code ParameterNumber}. */
    public record Ports(
            MarketPort market, MarketInventoryPort inventory, MarketGoldPort gold, MarketRecordPort records) {}

    /** Một lần bán vừa chốt — gateway báo người bán nếu đang online. */
    public record Sale(long listingId, long sellerId, long itemTemplateId, int quantity, long proceeds) {}

    /** Kết quả mua. */
    public record BuyResult(MarketOutcome outcome, Optional<Sale> sale) {}

    private final Ports ports;
    private final TransactionRunner tx;
    private final Clock clock;

    public MarketUseCase(Ports ports, TransactionRunner tx, Clock clock) {
        this.ports = ports;
        this.tx = tx;
        this.clock = clock;
    }

    /** Treo {@code quantity} cái của dòng túi {@code inventoryItemId} với giá TỔNG {@code price}. */
    public MarketOutcome create(ListingRequest request) {
        if (!MarketPolicy.isValidPrice(request.price())) {
            return MarketOutcome.INVALID_PRICE;
        }
        if (request.quantity() < 1) {
            return MarketOutcome.INVALID_QUANTITY;
        }
        Instant now = clock.instant();
        long sellerId = request.sellerId();
        int quantity = request.quantity();
        long price = request.price();
        return run(t -> {
            ports.inventory().lock(t, sellerId);
            ports.records().recordKey(t, request.idempotencyKey());
            if (ports.market().countActive(t, sellerId) >= MarketPolicy.MAX_ACTIVE_LISTINGS) {
                throw new Abort(MarketOutcome.TOO_MANY_LISTINGS);
            }
            long templateId =
                    switch (ports.inventory().take(t, sellerId, request.inventoryItemId(), quantity)) {
                        case MarketInventoryPort.TakeResult.Taken taken -> taken.itemTemplateId();
                        case MarketInventoryPort.TakeResult.Refused refused -> throw new Abort(refused.outcome());
                    };
            long id = ports.market()
                    .insert(
                            t,
                            new ListingDraft(
                                    sellerId, templateId, quantity, price, now.plus(MarketPolicy.LISTING_DURATION)));
            ports.records().audit(t, sellerId, "MARKET_LIST", detail(id, templateId, quantity, price));
        });
    }

    /** Mua cả dòng {@code listingId}. */
    public BuyResult buy(long buyerId, long listingId, String key) {
        Instant now = clock.instant();
        AtomicReference<Sale> sale = new AtomicReference<>();
        MarketOutcome outcome = run(t -> {
            ports.inventory().lock(t, buyerId);
            MarketListing listing =
                    ports.market().lockActive(t, listingId, now).orElseThrow(() -> new Abort(MarketOutcome.NOT_FOUND));
            if (listing.sellerId() == buyerId) {
                throw new Abort(MarketOutcome.OWN_LISTING);
            }
            ports.records().recordKey(t, key);
            if (!ports.gold().deduct(t, buyerId, listing.price())) {
                throw new Abort(MarketOutcome.INSUFFICIENT_GOLD);
            }
            long proceeds = MarketPolicy.proceedsOf(listing.price());
            ports.gold().credit(t, listing.sellerId(), proceeds);
            if (!ports.inventory().give(t, buyerId, listing.itemTemplateId(), listing.quantity())) {
                throw new Abort(MarketOutcome.BAG_FULL);
            }
            ports.market().markSold(t, listingId, buyerId, now);
            String detail = detail(listingId, listing.itemTemplateId(), listing.quantity(), listing.price());
            ports.records().audit(t, buyerId, "MARKET_BUY", detail);
            ports.records().audit(t, listing.sellerId(), "MARKET_SALE", detail);
            sale.set(new Sale(listingId, listing.sellerId(), listing.itemTemplateId(), listing.quantity(), proceeds));
        });
        return new BuyResult(outcome, Optional.ofNullable(sale.get()));
    }

    /** Người bán huỷ — món về túi (đầy ⇒ {@code BAG_FULL}, không gì đổi). */
    public MarketOutcome cancel(long sellerId, long listingId) {
        Instant now = clock.instant();
        return run(t -> {
            ports.inventory().lock(t, sellerId);
            MarketListing listing = ports.market()
                    .lockActive(t, listingId, now)
                    .filter(found -> found.sellerId() == sellerId)
                    .orElseThrow(() -> new Abort(MarketOutcome.NOT_FOUND));
            if (!ports.inventory().give(t, sellerId, listing.itemTemplateId(), listing.quantity())) {
                throw new Abort(MarketOutcome.BAG_FULL);
            }
            ports.market().markCancelled(t, listingId);
            ports.records()
                    .audit(
                            t,
                            sellerId,
                            "MARKET_CANCEL",
                            detail(listingId, listing.itemTemplateId(), listing.quantity(), listing.price()));
        });
    }

    public List<MarketListing> browse(Optional<Long> itemTemplateId) {
        return ports.market().browse(itemTemplateId, clock.instant(), MarketPolicy.BROWSE_LIMIT);
    }

    public List<MarketListing> mine(long sellerId) {
        return ports.market().listActiveBySeller(sellerId, clock.instant());
    }

    /**
     * Bước (1) của hết hạn (D4): chuyển dòng quá hạn sang {@code EXPIRED}, rồi trả những dòng chưa gửi trả
     * — gồm cả dòng của lượt trước bị gián đoạn — để gateway gửi thư rồi {@link #markReturned}.
     */
    public List<MarketListing> expireAndListUnreturned(int limit) {
        ports.market().expireDue(clock.instant());
        return ports.market().unreturnedExpired(limit);
    }

    public void markReturned(long listingId) {
        ports.market().markReturned(listingId);
    }

    private MarketOutcome run(java.util.function.Consumer<Transaction> work) {
        try {
            tx.inTransaction(work);
            return MarketOutcome.OK;
        } catch (Abort abort) {
            return abort.outcome;
        } catch (MarketRecordPort.DuplicateRequestException duplicate) {
            return MarketOutcome.DUPLICATE_REQUEST;
        }
    }

    private static String detail(long listingId, long itemTemplateId, int quantity, long price) {
        return "{\"listingId\":" + listingId + ",\"itemTemplateId\":" + itemTemplateId + ",\"quantity\":" + quantity
                + ",\"price\":" + price + "}";
    }

    /** Từ chối giữa transaction — ném để rollback, không "return sớm" (sẽ commit phần đã làm). */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient MarketOutcome outcome;

        Abort(MarketOutcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
