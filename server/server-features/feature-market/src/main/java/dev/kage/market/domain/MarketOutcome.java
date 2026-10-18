package dev.kage.market.domain;

/** Kết quả mọi lệnh phường thị (ADR-063). */
public enum MarketOutcome {
    OK,
    NOT_FOUND,
    INVALID_QUANTITY,
    INVALID_PRICE,
    EQUIPPED,
    ENHANCED_NOT_ALLOWED,
    ITEM_IN_TRADE,
    TOO_MANY_LISTINGS,
    OWN_LISTING,
    INSUFFICIENT_GOLD,
    BAG_FULL,
    DUPLICATE_REQUEST
}
