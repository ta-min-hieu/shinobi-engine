package dev.kage.shop.domain;

/** Kết quả mua của thương nhân du hành (ADR-118). */
public enum MerchantBuyOutcome {
    OK,
    /** Thương nhân không đang ở đây (chưa tới, đã đi, hoặc đứng xa trạm). */
    NOT_PRESENT,
    /** Món này đã bán hết cho cả server. */
    SOLD_OUT,
    NOT_FOUND,
    INSUFFICIENT_GOLD,
    BAG_FULL
}
