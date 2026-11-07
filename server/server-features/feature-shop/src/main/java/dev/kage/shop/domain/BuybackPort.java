package dev.kage.shop.domain;

import java.time.Instant;
import java.util.List;

/** Mua lại đồ đã bán cho cửa hàng NPC (ADR-202) — bảng {@code shop_buyback}. Chạm DB. */
public interface BuybackPort {

    /** Món đã bán từ {@code since} trở đi, mới nhất trước. */
    List<Entry> list(long characterId, Instant since);

    /** Trả lại đúng {@code price} vàng, nhận lại món (giữ cấp cường hoá), xoá dòng — một transaction. */
    Outcome buyBack(long characterId, long entryId, Instant since);

    /** Một món mua lại được. */
    record Entry(long id, long itemTemplateId, int quantity, int enhanceLevel, long price, Instant soldAt) {}

    /** Kết quả {@link #buyBack}. */
    enum Outcome {
        OK,
        /** Không có dòng đó của người gọi, hoặc đã quá hạn. */
        NOT_FOUND,
        INSUFFICIENT_GOLD,
        BAG_FULL
    }
}
