package dev.kage.gm.api;

import java.util.Optional;

import dev.kage.gm.domain.GmAuditEntry;
import dev.kage.gm.domain.GmAuditPort;
import dev.kage.gm.domain.GmOutcome;
import dev.kage.gm.domain.GmRole;
import dev.kage.gm.domain.GmRolePort;

/**
 * API của {@code feature-gm} (ADR-052 D9/D10): đọc quyền, và mở/chốt dòng audit. Lối DUY NHẤT để thi
 * hành một lệnh là đi qua {@link #begin}, trả {@link AuditTicket} — hàm thi hành ở gateway nhận ticket
 * làm tham số bắt buộc, nên thêm lệnh mới mà quên audit là lỗi biên dịch, không phải lỗi review.
 */
public final class GmFacade {

    /** Composition không có GM (test, hoặc DB chưa nối) — không ai có quyền. */
    public static final GmFacade DISABLED = new GmFacade(accountId -> Optional.empty(), new GmAuditPort() {
        @Override
        public long open(GmAuditEntry entry) {
            throw new IllegalStateException("GmFacade.DISABLED không ghi audit");
        }

        @Override
        public void close(long auditId, GmOutcome outcome, String detailJson) {
            throw new IllegalStateException("GmFacade.DISABLED không ghi audit");
        }
    });

    private final GmRolePort rolePort;
    private final GmAuditPort auditPort;

    /** Dựng ở composition root. */
    public GmFacade(GmRolePort rolePort, GmAuditPort auditPort) {
        this.rolePort = rolePort;
        this.auditPort = auditPort;
    }

    /** Đọc lại MỖI lệnh (D2) — thu quyền có hiệu lực ngay lệnh kế tiếp. */
    public Optional<GmRole> roleOf(long accountId) {
        return rolePort.findRole(accountId);
    }

    /** Ghi dòng {@code PENDING} (write-ahead, D5). Ném nếu không ghi được ⇒ caller KHÔNG thi hành. */
    public AuditTicket begin(GmAuditEntry entry) {
        return new AuditTicket(auditPort.open(entry), auditPort);
    }

    /** Một lệnh đã có dòng audit — phải {@link #finish} đúng một lần. */
    public static final class AuditTicket {
        private final long auditId;
        private final GmAuditPort auditPort;
        private boolean finished;

        AuditTicket(long auditId, GmAuditPort auditPort) {
            this.auditId = auditId;
            this.auditPort = auditPort;
        }

        /** Id dòng {@code gm_audit_log} — gắn vào {@code chat_mute.gm_audit_log_id}. */
        public long auditId() {
            return auditId;
        }

        /** Chốt kết quả; lần gọi thứ hai bị bỏ qua (dòng đã không còn PENDING). */
        public void finish(GmOutcome outcome, String detailJson) {
            if (finished) {
                return;
            }
            finished = true;
            auditPort.close(auditId, outcome, detailJson);
        }
    }
}
