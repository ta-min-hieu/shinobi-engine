package dev.kage.market.domain;

import java.time.Instant;
import java.util.List;

/** Theo dõi phường thị (ADR-196) — bảng {@code market_watch}. Chạm DB. */
public interface MarketWatchPort {

    /**
     * Đặt (hoặc sửa mức giá của) một dòng theo dõi.
     *
     * @return {@link Upsert#TOO_MANY} (không ghi) nếu đây là dòng MỚI mà nhân vật đã có {@code maxEntries} dòng;
     *     {@link Upsert#UNKNOWN_ITEM} nếu mẫu vật phẩm không tồn tại
     */
    Upsert upsert(WatchRequest request, int maxEntries);

    /** Bỏ theo dõi; {@code false} nếu không có dòng đó. */
    boolean remove(long characterId, long itemTemplateId);

    /** Các dòng theo dõi của {@code characterId}, mới nhất trước. */
    List<Watch> list(long characterId);

    /** Ai đang theo dõi {@code itemTemplateId} với mức giá {@code >= unitPrice}. */
    List<Long> watchersFor(long itemTemplateId, long unitPrice);

    /** Một dòng theo dõi. */
    record Watch(long itemTemplateId, long maxUnitPrice) {}

    /** Một lệnh đặt / sửa theo dõi. */
    record WatchRequest(long characterId, long itemTemplateId, long maxUnitPrice, Instant at) {}

    /** Kết quả {@link #upsert}. */
    enum Upsert {
        OK,
        TOO_MANY,
        UNKNOWN_ITEM
    }
}
