package dev.kage.world.domain;

import java.time.Instant;
import java.util.List;

/**
 * Linh điền (ADR-119, V99) — hiện thực ở server-database; mỗi lệnh ghi là MỘT giao dịch dưới khoá túi chung của người
 * thao tác (tưới chỉ ghi dòng ô của người kia, không chạm túi ai).
 */
public interface SpiritFieldPort {

    /** Kết quả các lệnh ghi. */
    enum Outcome {
        OK,
        INVALID_PLOT,
        PLOT_OCCUPIED,
        PLOT_EMPTY,
        NOT_RIPE,
        UNKNOWN_SEED,
        NO_SEED,
        ALREADY_WATERED,
        MAX_PLOTS,
        INSUFFICIENT_GOLD,
        BAG_FULL
    }

    /**
     * Một ô đang trồng.
     *
     * @param wateredBy 0 nếu chưa ai tưới
     */
    record Plot(
            int index, long seedTemplateId, long yieldTemplateId, Instant plantedAt, Instant ripeAt, long wateredBy) {}

    /** Ruộng của một người: số ô đã mở + các ô đang trồng. */
    record Field(int plots, List<Plot> planted) {

        public Field {
            planted = List.copyOf(planted);
        }
    }

    /** Một loại hạt giống. */
    record Seed(long itemTemplateId, int growMinutes, long yieldTemplateId, int yieldQuantity) {}

    List<Seed> seeds();

    Field field(long characterId);

    Outcome plant(long characterId, int plotIndex, long seedTemplateId, Instant now);

    /** Thu hoạch; {@code yieldOf} quyết sản lượng từ (gốc, đã tưới) — ngẫu nhiên ở phía gọi. */
    Outcome harvest(
            long characterId,
            int plotIndex,
            Instant now,
            java.util.function.BiFunction<Integer, Boolean, Integer> yieldOf);

    Outcome water(long waterCharacterId, long ownerId, int plotIndex, Instant now);

    Outcome unlockPlot(long characterId);
}
