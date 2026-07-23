package dev.kage.shop.domain;

/**
 * Cổng đổi gold của nhân vật — gold vật lý nằm ở {@code character_stat} (thuộc feature-character),
 * nhưng mua/bán (feature-shop) cần đổi gold NGUYÊN TỬ trong CÙNG transaction với thêm/bớt item
 * (DATABASE.md §5 — "trừ tiền nhưng không nhận item = mất tiền") nên khai báo cổng riêng ở đây
 * thay vì gọi qua {@code CharacterFacade} (Facade không nhận {@code Transaction} chia sẻ giữa 2
 * feature). Y hệt lý do {@code dev.kage.inventory.domain.GoldPort} đã dùng cho cường hoá — 2 bản
 * sao cùng ý tưởng vì mỗi feature không được phụ thuộc domain của feature khác. Adapter thật ở
 * {@code server-database} được phép đụng bảng {@code character_stat} vì module đó là hạ tầng dùng
 * chung cho MỌI feature, không phải "feature-character đọc bảng feature khác".
 */
public interface GoldPort {

    /**
     * Trừ {@code amount} gold của {@code characterId} NẾU đủ số dư — một câu lệnh UPDATE có điều
     * kiện ({@code WHERE gold >= amount}), tránh race đọc-rồi-ghi giữa 2 request mua đồng thời.
     *
     * @return {@code true} nếu trừ thành công (đủ gold), {@code false} nếu không đủ (không đổi gì)
     */
    boolean deductGold(Transaction tx, long characterId, long amount);

    /** Cộng {@code amount} gold cho {@code characterId} — dùng khi bán item, luôn thành công. */
    void creditGold(Transaction tx, long characterId, long amount);
}
