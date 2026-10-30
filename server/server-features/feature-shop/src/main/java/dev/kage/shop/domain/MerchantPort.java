package dev.kage.shop.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lịch + kho của thương nhân du hành (ADR-118, V98) — hiện thực ở server-database. */
public interface MerchantPort {

    List<MerchantOffer> offers();

    /** Số lần ghé đã lên lịch bắt đầu trong {@code [from, to)}. */
    int countVisitsStartingBetween(Instant from, Instant to);

    void insertVisit(String waypointCode, Instant startsAt, Instant endsAt, List<MerchantStock> stock);

    /** Lần ghé đang diễn ra lúc {@code now} (kèm kho còn lại). */
    Optional<MerchantVisit> active(Instant now);

    /** Lần ghé đang diễn ra mà chưa báo cả server — đánh dấu đã báo luôn (compare-and-set). */
    List<MerchantVisit> claimUnannouncedActive(Instant now);

    /**
     * Mua một cái món {@code itemTemplateId} của lần ghé {@code visitId}: MỘT giao dịch — khoá dòng kho (còn hàng,
     * lần ghé còn diễn ra) → khoá túi → trừ linh thạch → thêm vật phẩm → trừ kho → audit.
     */
    MerchantBuyOutcome buy(long characterId, long visitId, long itemTemplateId, Instant now);
}
