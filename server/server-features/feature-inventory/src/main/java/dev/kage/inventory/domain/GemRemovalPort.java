package dev.kage.inventory.domain;

import java.time.Instant;

/** Tháo Linh Ngọc (ADR-168) — gỡ viên ngọc đang khảm trên một trang bị, trả viên ngọc về túi, tốn phí vàng. */
public interface GemRemovalPort {

    /** Kết quả tháo. */
    enum Outcome {
        OK,
        /** Món không có trong túi người gửi. */
        NOT_FOUND,
        /** Món không đang khảm ngọc nào. */
        NOT_SOCKETED,
        NOT_ENOUGH_GOLD,
        /** Túi không còn chỗ cho viên ngọc — không tháo. */
        BAG_FULL
    }

    /** Kết quả + viên ngọc vừa về túi ({@code 0} khi không OK). */
    record Removed(Outcome outcome, long gemItemTemplateId) {}

    /** Một transaction: khoá túi, gỡ ô khảm, trừ phí, trả viên ngọc về túi, audit. */
    Removed unsocket(long characterId, long itemId, long fee, Instant at);
}
