package dev.kage.inventory.domain;

import java.time.Instant;
import java.util.List;

/**
 * Đọc lại lịch sử vật phẩm: ứng viên phân giải hàng loạt (ADR-232) và lịch sử cường hoá (ADR-233, từ audit
 * {@code ENHANCE_ITEM} sẵn có). Chỉ đọc.
 */
public interface ItemHistoryPort {

    /**
     * Một lần cường hoá.
     *
     * @param itemTemplateId mẫu của món; 0 nếu món không còn trong túi (đã phân giải / bán / vỡ)
     * @param result {@code SUCCESS}, {@code FAILED_NO_DROP}, {@code FAILED_DROPPED}… đúng tên trong audit
     */
    record EnhanceRecord(long itemTemplateId, int fromLevel, int toLevel, long goldCost, String result, Instant at) {}

    /**
     * Id các trang bị trong túi phân giải được (chưa mặc, chưa khoá, chưa khảm, không đang giao dịch) cấp yêu cầu ≤
     * {@code maxRequiredLevel}.
     */
    List<Long> dismantleCandidates(long characterId, int maxRequiredLevel);

    /** Tối đa {@code limit} lần cường hoá gần nhất, mới nhất trước. */
    List<EnhanceRecord> enhanceHistory(long characterId, int limit);

    /**
     * Một lần nhặt đồ (ADR-253, audit {@code LOOT_GRANT} sẵn có).
     *
     * @param source mã nguồn rơi (thường là mã quái)
     */
    record LootRecord(long itemTemplateId, int quantity, String source, Instant at) {}

    /** Tối đa {@code limit} lần nhặt đồ gần nhất, mới nhất trước. */
    List<LootRecord> lootHistory(long characterId, int limit);
}
