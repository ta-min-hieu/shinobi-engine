package dev.kage.inventory.domain;

import java.util.Set;

/**
 * Khoá vật phẩm (ADR-159). Cờ {@code inventory_item.locked}: đường làm món rời nhân vật đọc nó trong SQL của chính
 * đường đó (cùng chỗ kiểm "đang mặc"); port này chỉ bật/tắt cờ và liệt kê để client vẽ.
 */
public interface ItemLockPort {

    /** Kết quả bật/tắt. */
    enum Outcome {
        OK,
        /** Món không có trong túi người gửi. */
        NOT_FOUND
    }

    /** Bật/tắt khoá một món của chính nhân vật — khoá túi đồ trước (thứ tự khoá, DATABASE.md §5.1). */
    Outcome setLocked(long characterId, long itemId, boolean locked);

    /** {@code inventory_item.id} các món đang khoá của nhân vật. */
    Set<Long> lockedItems(long characterId);
}
