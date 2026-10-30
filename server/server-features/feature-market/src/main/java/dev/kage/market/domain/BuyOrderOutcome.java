package dev.kage.market.domain;

/** Kết quả lệnh cầu mua (ADR-116). */
public enum BuyOrderOutcome {
    OK,
    NOT_FOUND,
    UNKNOWN_ITEM,
    INVALID_QUANTITY,
    INVALID_PRICE,
    TOO_MANY_ORDERS,
    INSUFFICIENT_GOLD,
    /** Người đặt tự bán vào lệnh của mình. */
    OWN_ORDER,
    /** Dòng túi đưa ra không phải đúng loại lệnh cần. */
    WRONG_ITEM,
    EQUIPPED,
    ENHANCED_NOT_ALLOWED,
    ITEM_IN_TRADE,
    /** Túi người cầu mua đã đầy — không nhận thêm được. */
    BUYER_BAG_FULL
}
