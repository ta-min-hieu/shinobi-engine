package dev.kage.market.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử phường thị (ADR-177) — các dòng ĐÃ BÁN mà nhân vật là người bán hoặc người mua. Chạm DB. */
public interface MarketHistoryPort {

    /**
     * Một giao dịch đã chốt. {@code sold} = mình là người bán (nhận {@link MarketPolicy#proceedsOf}), ngược lại mình là
     * người mua (trả {@code price}). {@code counterpartName} = tên người kia ("" nếu nhân vật đã bị xoá).
     */
    record Entry(
            long listingId,
            boolean sold,
            long itemTemplateId,
            int quantity,
            long price,
            String counterpartName,
            Instant closedAt) {}

    /** Mới nhất trước, chốt từ {@code since} trở đi, tối đa {@code limit} dòng. */
    List<Entry> recent(long characterId, Instant since, int limit);
}
