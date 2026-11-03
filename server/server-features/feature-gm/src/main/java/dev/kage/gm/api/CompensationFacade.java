package dev.kage.gm.api;

import java.time.Instant;
import java.util.List;

import dev.kage.gm.domain.CompensationPort;

/** Thư bồi thường hàng loạt (ADR-165). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class CompensationFacade {

    /** Composition không có bồi thường (test): mọi đợt coi như mã đã dùng. */
    public static final CompensationFacade DISABLED = new CompensationFacade(new CompensationPort() {
        @Override
        public Start begin(Campaign campaign, long createdBy, Instant at) {
            return Start.CONFLICT;
        }

        @Override
        public List<Long> recipients(int minLevel) {
            return List.of();
        }

        @Override
        public void finish(String code, int sent, Instant at) {
            // không có gì để chốt
        }
    });

    private final CompensationPort port;

    /** Dựng ở composition root. */
    public CompensationFacade(CompensationPort port) {
        this.port = port;
    }

    /** Mở (hoặc chạy tiếp) một đợt. */
    public CompensationPort.Start begin(CompensationPort.Campaign campaign, long createdBy, Instant at) {
        return port.begin(campaign, createdBy, at);
    }

    /** Người nhận của đợt. */
    public List<Long> recipients(int minLevel) {
        return port.recipients(minLevel);
    }

    /** Chốt đợt. */
    public void finish(String code, int sent, Instant at) {
        port.finish(code, sent, at);
    }
}
