package dev.kage.world.domain;

import java.time.LocalDate;

/**
 * Quét nhanh Bí Cảnh (ADR-162) — Bí Cảnh đã từng hạ boss thì nhận thưởng ngay thay vì đánh lại: tốn một lượt ngày
 * (chung với lượt vào thật, ADR-099) và phí vàng; EXP cộng thẳng, bảng rơi do phía gọi tung và gửi thư.
 */
public interface DungeonSweepPort {

    /** Kết quả quét. */
    enum Outcome {
        OK,
        /** Bí Cảnh không có luật quét. */
        UNKNOWN_DUNGEON,
        /** Chưa từng hạ boss Bí Cảnh này. */
        NOT_CLEARED,
        DAILY_LIMIT_REACHED,
        NOT_ENOUGH_GOLD
    }

    /**
     * Một lần quét.
     *
     * @param dropTableCode bảng rơi phía gọi phải tung (rỗng khi không OK)
     * @param entriesUsed số lượt đã dùng hôm nay SAU lần quét
     */
    record Result(Outcome outcome, long expGained, String dropTableCode, int entriesUsed) {

        /** Từ chối — không đổi gì. */
        public static Result refused(Outcome outcome, int entriesUsed) {
            return new Result(outcome, 0L, "", entriesUsed);
        }
    }

    /** Một transaction: kiểm lần đầu hạ boss, trừ lượt, trừ vàng, cộng EXP, audit. */
    Result sweep(long characterId, String dungeonCode, LocalDate day, int maxEntries);
}
