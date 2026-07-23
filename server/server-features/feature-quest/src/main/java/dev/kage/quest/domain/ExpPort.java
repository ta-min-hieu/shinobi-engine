package dev.kage.quest.domain;

/**
 * Cổng cộng EXP nhân vật — EXP vật lý nằm ở {@code character.exp} (thuộc feature-character), nhưng
 * trao thưởng quest cần cộng EXP + cấp item + ghi audit NGUYÊN TỬ trong CÙNG transaction với đổi
 * {@code status=CLAIMED} — lý do khai báo port riêng ở đây Y HỆT
 * {@code dev.kage.shop.domain.GoldPort}/{@code dev.kage.inventory.domain.GoldPort}: mỗi feature tự
 * định nghĩa {@code Transaction} riêng, không share được qua Facade. Adapter thật ở
 * {@code server-database} được phép đụng bảng {@code character} vì module đó là hạ tầng dùng
 * chung, không phải "feature-quest đọc bảng feature khác".
 */
public interface ExpPort {

    void grantExp(Transaction tx, long characterId, long amount);
}
