package dev.kage.inventory.domain;

/**
 * Cổng ghi {@code audit_log} — BẮT BUỘC cho cường hoá (DATABASE.md §2.7/§5): mất tài sản mà không
 * điều tra được là không chấp nhận được. Ghi trong CÙNG transaction với thao tác gây ra nó, không
 * bao giờ ghi audit sau khi transaction đã commit (mất tính nguyên tử — có thể ghi audit nhưng
 * thao tác thật lại rollback, hoặc ngược lại).
 *
 * <p>Định nghĩa RIÊNG trong {@code feature-inventory} (không phải cổng dùng chung) — cùng lý do
 * {@code TransactionRunner} bị lặp lại ở mỗi feature (Clean Architecture: domain không import
 * domain của feature khác). Feature khác cần audit (trade, shop — Phase 7+) sẽ tự khai báo
 * {@code AuditPort} riêng của nó khi tới lượt, KHÔNG tái sử dụng interface này.
 */
public interface AuditPort {

    void log(Transaction tx, long characterId, String action, String detailJson);
}
