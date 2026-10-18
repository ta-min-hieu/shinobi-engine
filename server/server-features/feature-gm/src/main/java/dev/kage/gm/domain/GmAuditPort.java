package dev.kage.gm.domain;

/**
 * Sổ {@code gm_audit_log} (ADR-052 D5) — ghi TRƯỚC khi lệnh có hiệu lực ({@link #open}), chốt đúng MỘT
 * lần sau đó ({@link #close}). Ngoài hai câu đó bảng chỉ có INSERT — không bao giờ xoá.
 */
public interface GmAuditPort {

    /** Dòng mới {@code outcome='PENDING'}; ném nếu không ghi được ⇒ lệnh KHÔNG chạy. */
    long open(GmAuditEntry entry);

    /** {@code PENDING → outcome}; dòng không còn PENDING thì không đổi gì. */
    void close(long auditId, GmOutcome outcome, String detailJson);
}
