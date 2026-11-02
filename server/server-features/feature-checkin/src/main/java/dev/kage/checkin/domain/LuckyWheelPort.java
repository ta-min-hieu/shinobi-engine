package dev.kage.checkin.domain;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Vòng quay may mắn (ADR-151, V122) — hiện thực ở server-database. Quay là MỘT giao dịch dưới khoá túi của nhân vật:
 * đếm lượt trong ngày, trừ linh thạch (lượt mua), trao thưởng, ghi lượt + audit.
 */
public interface LuckyWheelPort {

    /** Kết quả quay. */
    enum Outcome {
        OK,
        NO_SPINS_LEFT,
        INSUFFICIENT_GOLD,
        BAG_FULL,
        GOLD_CAP
    }

    /**
     * Một ô thưởng.
     *
     * @param itemTemplateId 0 nếu ô chỉ có linh thạch
     */
    record Prize(int index, String labelKey, long gold, long itemTemplateId, int quantity) {}

    /** Số lượt còn lại trong ngày. */
    record Spins(int freeLeft, int paidLeft) {}

    /** Vòng quay hiện tại của nhân vật. */
    record Wheel(List<Prize> prizes, Spins spins) {

        public Wheel {
            prizes = List.copyOf(prizes);
        }
    }

    /** Kết quả một lượt: {@code prize} có khi {@code OK}; {@code spins} là số lượt còn lại SAU lệnh. */
    record Spin(Outcome outcome, Optional<Prize> prize, boolean paid, Spins spins) {}

    /** Các ô thưởng + số lượt còn lại của ngày {@code day} (không khoá, chỉ đọc). */
    Wheel wheel(long characterId, LocalDate day);

    /**
     * Quay một lượt trong ngày {@code day}: còn lượt miễn phí thì dùng nó, không thì lượt mua (trừ
     * {@link LuckyWheelRules#SPIN_COST}); {@code pick} chọn chỉ số ô từ mảng trọng số (theo id). Từ chối ở bước nào thì
     * rollback trọn — không mất lượt, không mất tiền.
     */
    Spin spin(long characterId, LocalDate day, Instant now, ToIntFunction<int[]> pick);
}
