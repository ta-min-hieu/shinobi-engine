package dev.kage.mail.domain;

/**
 * Cổng cộng gold — bản sao ý tưởng {@code dev.kage.shop.domain.GoldPort}, chỉ cần chiều CỘNG
 * (nhận thư luôn thành công cộng gold, không có nhánh "không đủ" như mua đồ). Gửi thư KHÔNG mất
 * gold ở MVP này (feature-mail/CLAUDE.md) nên không cần {@code deductGold}.
 */
public interface GoldPort {

    void creditGold(Transaction tx, long characterId, long amount);
}
