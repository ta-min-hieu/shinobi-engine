package dev.kage.inventory.domain;

/**
 * Phân giải pháp khí (ADR-097) — hiện thực ở server-database: MỘT giao dịch dưới khoá túi đồ chung (khoá túi → khoá
 * dòng món → kiểm → xoá → cộng Linh Thảo → cộng vàng qua GoldLedger → audit). Thu về tính bằng {@link DismantlePolicy}.
 */
public interface DismantlePort {

    /** Kết quả. */
    enum Outcome {
        OK,
        NOT_FOUND,
        NOT_DISMANTLABLE,
        EQUIPPED,
        SOCKETED,
        ITEM_IN_TRADE
    }

    /**
     * Kết quả kèm thu về.
     *
     * @param gold linh thạch đã cộng (0 nếu từ chối)
     * @param herbs Linh Thảo đã cộng (0 nếu từ chối)
     */
    record Result(Outcome outcome, long gold, int herbs) {

        public static Result refused(Outcome outcome) {
            return new Result(outcome, 0L, 0);
        }
    }

    Result dismantle(long characterId, long itemId);
}
