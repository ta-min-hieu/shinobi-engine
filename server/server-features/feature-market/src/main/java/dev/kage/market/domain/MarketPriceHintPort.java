package dev.kage.market.domain;

import java.time.Instant;

/** Giá gợi ý khi rao bán (ADR-234) — đọc thẳng các dòng rao đang mở và đã bán của phường thị. Chỉ đọc. */
public interface MarketPriceHintPort {

    /**
     * Giá MỘT cái (giá dòng chia số lượng, làm tròn xuống).
     *
     * @param lowestActive giá rẻ nhất đang rao; 0 nếu không ai rao
     * @param averageSold giá bán trung bình từ {@code since}; 0 nếu chưa bán được cái nào
     * @param soldListings số dòng đã bán từ {@code since}
     */
    record Hint(long lowestActive, long averageSold, int soldListings) {

        /** Chưa có dữ liệu. */
        public static final Hint NONE = new Hint(0L, 0L, 0);
    }

    Hint hint(long itemTemplateId, Instant since);
}
