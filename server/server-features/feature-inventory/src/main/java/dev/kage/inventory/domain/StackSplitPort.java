package dev.kage.inventory.domain;

/** Tách chồng vật phẩm (ADR-215) — một transaction trọn trong adapter. */
public interface StackSplitPort {

    /** Kết quả một lần tách. */
    enum Outcome {
        OK,
        /** Không có trong túi, đang mặc, hoặc đang chào giao dịch. */
        NOT_FOUND,
        /** Số tách phải từ 1 tới số đang có trừ 1. */
        INVALID_AMOUNT,
        BAG_FULL
    }

    /** Lấy {@code amount} cái khỏi chồng {@code inventoryItemId} sang một ô mới cuối túi. */
    Outcome split(long characterId, long inventoryItemId, int amount);
}
