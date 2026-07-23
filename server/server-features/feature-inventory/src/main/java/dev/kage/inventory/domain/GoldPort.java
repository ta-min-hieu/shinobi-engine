package dev.kage.inventory.domain;

/**
 * Cổng trừ gold của nhân vật — gold vật lý nằm ở {@code character_stat} (thuộc feature-character),
 * nhưng cường hoá (feature-inventory) cần trừ NGUYÊN TỬ trong CÙNG transaction với đổi
 * {@code enhance_level} (DATABASE.md §5) nên khai báo cổng riêng ở đây thay vì gọi qua
 * {@code CharacterFacade} (Facade không nhận {@code Transaction} chia sẻ giữa 2 feature). Adapter
 * thật ở {@code server-database} được phép đụng bảng {@code character_stat} vì module đó là hạ
 * tầng dùng chung cho MỌI feature, không phải "feature-character đọc bảng feature khác".
 */
public interface GoldPort {

    /**
     * Trừ {@code amount} gold của {@code characterId} NẾU đủ số dư — một câu lệnh UPDATE có điều
     * kiện ({@code WHERE gold >= amount}), tránh race đọc-rồi-ghi giữa 2 request cường hoá đồng thời.
     *
     * @return {@code true} nếu trừ thành công (đủ gold), {@code false} nếu không đủ (không đổi gì)
     */
    boolean deductGold(Transaction tx, long characterId, long amount);
}
