package dev.kage.mail.domain;

/**
 * Cổng ghi {@code audit_log} (bảng chung, V7) cho hành vi nhận thư — bản sao ý tưởng
 * {@code dev.kage.shop.domain.AuditPort}, mỗi feature khai báo port riêng vì domain không phụ
 * thuộc domain của feature khác.
 */
public interface AuditPort {

    void log(Transaction tx, long characterId, String action, String detailJson);
}
