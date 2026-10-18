package dev.kage.market.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lưu trữ dòng treo bán (ADR-063) — hiện thực ở server-database. */
public interface MarketPort {

    /** Khoá dòng còn {@code ACTIVE} và chưa hết hạn; rỗng nếu không còn mua/huỷ được. */
    Optional<MarketListing> lockActive(Transaction tx, long listingId, Instant now);

    int countActive(Transaction tx, long sellerId);

    long insert(Transaction tx, ListingDraft draft);

    void markSold(Transaction tx, long listingId, long buyerId, Instant at);

    void markCancelled(Transaction tx, long listingId);

    /** Dòng đang bán, mới nhất trước; {@code itemTemplateId} rỗng = mọi loại. */
    List<MarketListing> browse(Optional<Long> itemTemplateId, Instant now, int limit);

    /** Dòng ĐANG BÁN của một người. */
    List<MarketListing> listActiveBySeller(long sellerId, Instant now);

    /** {@code ACTIVE → EXPIRED} cho mọi dòng quá hạn (compare-and-set); số dòng vừa chuyển. */
    int expireDue(Instant now);

    /** Dòng {@code EXPIRED} chưa gửi trả — tối đa {@code limit}. */
    List<MarketListing> unreturnedExpired(int limit);

    void markReturned(long listingId);
}
