package dev.kage.mail.domain;

/**
 * Cổng đổi gold — bản sao ý tưởng {@code dev.kage.shop.domain.GoldPort}, và từ 2026-08-28 cũng có
 * đủ HAI chiều như nó, vì thư có đủ hai chiều tiền thật: trừ của người gửi lúc gửi (ký quỹ), cộng
 * cho người nhận lúc nhận.
 *
 * <p>Trước đó chỉ có chiều CỘNG — javadoc cũ ghi "gửi thư KHÔNG mất gold ở MVP" nên không cần
 * {@code deductGold}. Nhưng {@code gold_amount} vẫn được ghi vào thư từ packet của client, còn
 * {@code ClaimMailUseCase} thì cộng thật: cộng mà không ai bị trừ chính là đúc vàng từ không khí.
 */
public interface GoldPort {

    /**
     * Trừ {@code amount} gold của {@code characterId} NẾU đủ số dư — một câu UPDATE có điều kiện
     * ({@code WHERE gold >= amount}), tránh race đọc-rồi-ghi giữa hai request gửi thư đồng thời.
     *
     * @return {@code true} nếu trừ thành công, {@code false} nếu không đủ (KHÔNG đổi gì)
     */
    boolean deductGold(Transaction tx, long characterId, long amount);

    /** Cộng {@code amount} gold — nhận thư luôn thành công (không có trần số dư ở lát cắt này). */
    void creditGold(Transaction tx, long characterId, long amount);
}
