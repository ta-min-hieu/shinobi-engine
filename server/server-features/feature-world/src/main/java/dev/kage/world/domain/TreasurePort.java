package dev.kage.world.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.function.ToIntFunction;

/**
 * Tàng Bảo Đồ (ADR-147, V119) — hiện thực ở server-database. Đọc bản đồ và đào đều là MỘT giao dịch mở đầu bằng khoá
 * túi của nhân vật (thứ tự khoá chung), nên hai lệnh song song không thể tiêu hai tấm bản đồ cho một cuộc tìm hay đào
 * một kho hai lần.
 */
public interface TreasurePort {

    /** Kết quả đọc bản đồ hoặc đào. */
    enum Outcome {
        OK,
        NO_HUNT,
        NO_MAP,
        ALREADY_HUNTING,
        BAG_FULL
    }

    /** Một điểm chôn đang được tìm. */
    record Spot(long id, String nameKey, float x, float y, String guardianMonsterCode) {}

    /** Kết quả đọc bản đồ: {@code spot} là cuộc tìm đang mở SAU lệnh (cả khi {@code ALREADY_HUNTING}). */
    record ReadResult(Outcome outcome, Optional<Spot> spot) {}

    /**
     * Kết quả đào. {@code spawnGuardian} thì phía gọi sinh quái canh kho của {@code spot} cạnh người chơi.
     *
     * @param itemTemplateId 0 nếu không thưởng vật phẩm
     */
    record DigResult(
            Outcome outcome,
            Optional<Spot> spot,
            String resultKey,
            long gold,
            long itemTemplateId,
            int quantity,
            boolean spawnGuardian) {

        /** Đào bị từ chối — không thưởng gì; {@code spot} là cuộc tìm còn lại (nếu có). */
        public static DigResult refused(Outcome outcome, Optional<Spot> spot) {
            return new DigResult(outcome, spot, "", 0L, 0L, 0, false);
        }
    }

    /** Cuộc tìm đang mở của nhân vật (không khoá, chỉ đọc). */
    Optional<Spot> activeHunt(long characterId);

    /**
     * Đọc một Tàng Bảo Đồ: đang có cuộc tìm ⇒ {@code ALREADY_HUNTING} (không tiêu); không có bản đồ ⇒ {@code NO_MAP};
     * còn lại tiêu một tấm và mở cuộc tìm ở điểm {@code pickSpot} chọn (chỉ số trong danh sách điểm theo id; mảng
     * truyền vào là số điểm, phần tử nào cũng bằng 1 — chọn đều).
     */
    ReadResult read(long characterId, Instant now, ToIntFunction<int[]> pickSpot);

    /**
     * Đào kho của cuộc tìm đang mở ở đúng {@code expectedSpotId} (phía gọi đã kiểm khoảng cách tới điểm này): tung
     * một phần thưởng bằng {@code pickReward} (mảng trọng số theo id), cấp trong cùng giao dịch, đóng cuộc tìm, ghi
     * audit {@code TREASURE_DIG}. Túi đầy ⇒ {@code BAG_FULL}, cuộc tìm vẫn còn.
     */
    DigResult dig(long characterId, long expectedSpotId, Instant now, ToIntFunction<int[]> pickReward);
}
