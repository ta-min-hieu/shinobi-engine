package dev.kage.market.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Bảng lệnh cầu mua (ADR-116, V96) — hiện thực ở server-database. Vòng đời ACTIVE → FILLED | CANCELLED | EXPIRED. */
public interface BuyOrderPort {

    boolean templateExists(Transaction tx, long itemTemplateId);

    int countActive(Transaction tx, long buyerId);

    long insert(Transaction tx, NewBuyOrder order);

    /** Người đặt lệnh — đọc KHÔNG khoá (để biết khoá túi ai trước khi khoá dòng). */
    Optional<Long> buyerOf(Transaction tx, long orderId);

    /** Khoá dòng còn ACTIVE và chưa quá hạn — rỗng nếu không còn. */
    Optional<BuyOrder> lockActive(Transaction tx, long orderId, Instant now);

    /** Cộng số đã khớp; đủ thì sang FILLED. */
    void addFilled(Transaction tx, long orderId, int quantity, Instant now);

    void markCancelled(Transaction tx, long orderId, Instant now);

    /** Lệnh đang mở, giá cao trước; lọc loại tuỳ chọn. */
    List<BuyOrder> browse(Optional<Long> itemTemplateId, Instant now, int limit);

    List<BuyOrder> mine(long buyerId, Instant now);

    /** ACTIVE quá hạn → EXPIRED. */
    void expireDue(Instant now);

    /** EXPIRED còn ký quỹ chưa hoàn. */
    List<BuyOrder> unrefundedExpired(int limit);

    void markRefunded(long orderId);
}
