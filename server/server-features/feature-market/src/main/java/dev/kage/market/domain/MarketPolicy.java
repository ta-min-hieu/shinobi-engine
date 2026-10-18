package dev.kage.market.domain;

import java.time.Duration;

/** Luật phường thị (ADR-063 D3/D5) — hàm thuần. */
public final class MarketPolicy {

    public static final int FEE_PERCENT = 5;
    public static final int MAX_ACTIVE_LISTINGS = 10;
    public static final long MIN_PRICE = 1L;
    public static final long MAX_PRICE = 10_000_000L;
    public static final Duration LISTING_DURATION = Duration.ofDays(2);
    public static final int BROWSE_LIMIT = 50;

    private static final int PERCENT = 100;

    private MarketPolicy() {}

    /** Phí của một lần bán: {@code floor(giá × 5%)}, tối thiểu 1 — số linh thạch biến mất (sink). */
    public static long feeOf(long price) {
        return Math.max(1L, price * FEE_PERCENT / PERCENT);
    }

    /** Người bán nhận về. */
    public static long proceedsOf(long price) {
        return price - feeOf(price);
    }

    public static boolean isValidPrice(long price) {
        return price >= MIN_PRICE && price <= MAX_PRICE;
    }
}
