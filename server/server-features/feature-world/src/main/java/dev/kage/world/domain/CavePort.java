package dev.kage.world.domain;

import java.time.Instant;
import java.util.List;

/**
 * Động phủ (ADR-129, V108) — hiện thực ở server-database. Mọi lệnh ghi là MỘT giao dịch khoá dòng động phủ của người
 * gửi; lượt nâng cấp và bày trí THU kho trước (theo luật cũ) để không ai mất phần đã tích.
 */
public interface CavePort {

    /** Kết quả các lệnh ghi. */
    enum Outcome {
        OK,
        NOTHING_TO_COLLECT,
        MAX_LEVEL,
        INSUFFICIENT_GOLD,
        UNKNOWN_DECORATION,
        ALREADY_PLACED,
        /** Thu vào thì vượt trần linh thạch mang theo — không đổi gì. */
        GOLD_CAP
    }

    /** Một món bày trí trong danh mục. */
    record Decoration(String code, String nameKey, long goldCost, int bonusPercent) {}

    /**
     * Động phủ của một người.
     *
     * @param decorations mã các món đã bày
     */
    record Cave(int veinLevel, Instant lastCollectedAt, List<String> decorations) {

        public Cave {
            decorations = List.copyOf(decorations);
        }
    }

    /** Kết quả ghi kèm số linh thạch vừa thu vào túi (0 nếu không thu). */
    record Result(Outcome outcome, long collected) {}

    /** Danh mục bày trí. */
    List<Decoration> catalog();

    /** Động phủ của {@code characterId}; chưa có thì dựng (cấp 1, bắt đầu tích từ {@code now}). */
    Cave cave(long characterId, Instant now);

    /** Thu linh thạch đã tích. */
    Result collect(long characterId, Instant now);

    /** Thu kho rồi nâng linh mạch lên một cấp. */
    Result upgrade(long characterId, Instant now);

    /** Thu kho rồi mua + bày món {@code code}. */
    Result decorate(long characterId, String code, Instant now);
}
