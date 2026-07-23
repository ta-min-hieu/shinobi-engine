package dev.kage.shop.domain;

/**
 * Cổng ghi {@code audit_log} (bảng chung, V7) cho hành vi mua/bán — bản sao ý tưởng của
 * {@code dev.kage.inventory.domain.AuditPort} (cường hoá), mỗi feature khai báo port riêng vì
 * domain không phụ thuộc domain của feature khác.
 */
public interface AuditPort {

    void log(Transaction tx, long characterId, String action, String detailJson);
}
