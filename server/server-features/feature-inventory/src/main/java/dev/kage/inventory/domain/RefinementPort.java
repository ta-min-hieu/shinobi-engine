package dev.kage.inventory.domain;

import java.util.List;

/**
 * Tẩy luyện pháp khí (ADR-111) — hiện thực ở server-database: MỘT giao dịch dưới khoá túi đồ chung (khoá túi → món đang
 * mặc ở ô có chỉ số → đủ đá → trừ đá → ghi dòng mới → audit {@code ITEM_REFINED}). Dòng gieo bằng
 * {@link RefinementPolicy#reroll}.
 */
public interface RefinementPort {

    /** Kết quả. */
    enum Outcome {
        OK,
        /** Món không thuộc về người gửi. */
        NOT_FOUND,
        /** Món không đang mặc ở một ô có chỉ số. */
        NOT_REFINABLE,
        NOT_ENOUGH_STONES,
        /** Dòng khoá không tồn tại (kể cả món chưa từng tẩy). */
        INVALID_LOCK
    }

    /**
     * Một món đang mặc kèm dòng tẩy luyện.
     *
     * @param slot tên ô ({@link EquipmentSlotType})
     */
    record WornItem(long itemId, long itemTemplateId, String slot, List<RefinementPolicy.Line> lines) {

        public WornItem {
            lines = List.copyOf(lines);
        }
    }

    /** Kết quả kèm món sau khi tẩy (rỗng {@code lines} khi từ chối). */
    record Result(Outcome outcome, List<RefinementPolicy.Line> lines) {

        public Result {
            lines = List.copyOf(lines);
        }

        public static Result refused(Outcome outcome) {
            return new Result(outcome, List.of());
        }
    }

    /** Các món đang mặc ở ô có chỉ số, theo thứ tự ô. */
    List<WornItem> wornItems(long characterId);

    /** Tổng chỉ số dòng tẩy luyện của các món ĐANG MẶC. */
    EquipmentBonus wornBonusOf(long characterId);

    /** Tẩy món {@code itemId}; {@code lockedLine} = {@link RefinementPolicy#NO_LOCK} nếu không khoá. */
    Result refine(long characterId, long itemId, int lockedLine);
}
