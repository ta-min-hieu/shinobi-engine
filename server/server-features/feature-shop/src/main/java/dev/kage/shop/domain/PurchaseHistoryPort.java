package dev.kage.shop.domain;

import java.time.Instant;
import java.util.List;

/** Lịch sử mua cửa hàng NPC (ADR-288) — đọc audit {@code SHOP_BUY} mà {@code BuyItemUseCase} đã ghi sẵn. */
public interface PurchaseHistoryPort {

    /** Một lần mua. */
    record Purchase(long itemTemplateId, int quantity, long goldCost, Instant at) {}

    /** Tối đa {@code limit} lần mua gần nhất, mới trước. */
    List<Purchase> recent(long characterId, int limit);
}
