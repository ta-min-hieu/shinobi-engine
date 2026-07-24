package dev.kage.trade.domain;

/**
 * Cổng đổi gold — bản sao ý tưởng {@code dev.kage.shop.domain.GoldPort} (mỗi feature tự khai báo
 * riêng, không phụ thuộc domain feature khác). CHỈ dùng lúc THI HÀNH (không escrow lúc đặt lời
 * chào — xem feature-trade/CLAUDE.md).
 */
public interface TradeGoldPort {

    /**
     * Trừ {@code amount} gold NẾU đủ số dư — 1 UPDATE có điều kiện, tránh race đọc-rồi-ghi.
     *
     * @return {@code true} nếu trừ thành công (đủ gold), {@code false} nếu không đủ (không đổi gì)
     */
    boolean deductGold(Transaction tx, long characterId, long amount);

    /** Cộng {@code amount} gold — luôn thành công. */
    void creditGold(Transaction tx, long characterId, long amount);
}
