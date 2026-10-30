package dev.kage.market.domain;

import java.time.Duration;

/** Luật cầu mua (ADR-116) — hàm thuần. Phí bán vào lệnh dùng chung {@link MarketPolicy#feeOf}. */
public final class BuyOrderPolicy {

    public static final int MAX_ACTIVE_ORDERS = 5;
    public static final int MAX_QUANTITY = 999;
    public static final Duration ORDER_DURATION = Duration.ofDays(2);

    private BuyOrderPolicy() {}

    public static boolean isValidQuantity(int quantity) {
        return quantity >= 1 && quantity <= MAX_QUANTITY;
    }

    /** Đơn giá theo cùng biên giá phường thị. */
    public static boolean isValidUnitPrice(long unitPrice) {
        return MarketPolicy.isValidPrice(unitPrice);
    }

    /** Ký quỹ của lệnh. */
    public static long escrowOf(int quantity, long unitPrice) {
        return Math.multiplyExact(quantity, unitPrice);
    }
}
