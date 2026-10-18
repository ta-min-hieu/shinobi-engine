package dev.kage.market.domain;

/** Túi đồ, nhìn từ phường thị — trong CÙNG transaction với dòng treo và vàng (ADR-063 D2/D3). */
public interface MarketInventoryPort {

    /** Khoá túi đồ của nhân vật (khoá tư vấn chung với mọi đường ghi túi — DATABASE.md §5.1). */
    void lock(Transaction tx, long characterId);

    /** Lấy {@code quantity} cái từ dòng túi {@code inventoryItemId} của người bán. */
    TakeResult take(Transaction tx, long characterId, long inventoryItemId, int quantity);

    /**
     * Thêm vào túi (gộp chồng trước, rồi mở ô theo {@code max_stack}).
     *
     * @return {@code false} nếu túi không đủ chỗ — không ghi gì
     */
    boolean give(Transaction tx, long characterId, long itemTemplateId, int quantity);

    /** Kết quả lấy đồ khỏi túi. */
    sealed interface TakeResult {
        record Taken(long itemTemplateId) implements TakeResult {}

        record Refused(MarketOutcome outcome) implements TakeResult {}
    }
}
