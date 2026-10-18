package dev.kage.chat.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Khoá chat theo TÀI KHOẢN ({@code chat_mute}, V61 — ADR-052 D4). feature-chat không biết GM là gì: nó
 * chỉ biết "tài khoản này bị khoá tới lúc nào". Dòng hết hạn coi như không có.
 */
public interface ChatMutePort {

    /** Không ai bị khoá — composition không nối bảng {@code chat_mute} (test cũ). */
    ChatMutePort NONE = new ChatMutePort() {
        @Override
        public Optional<Instant> mutedUntil(long accountId) {
            return Optional.empty();
        }

        @Override
        public void mute(long accountId, Instant until, long auditId) {
            throw new UnsupportedOperationException("ChatMutePort.NONE không khoá được ai");
        }

        @Override
        public boolean unmute(long accountId) {
            return false;
        }
    };

    Optional<Instant> mutedUntil(long accountId);

    /** Ghi đè lần khoá cũ (nếu có). {@code auditId}: dòng {@code gm_audit_log} ra lệnh khoá. */
    void mute(long accountId, Instant until, long auditId);

    /** Xoá dòng khoá; trả {@code true} nếu đã có dòng để xoá. */
    boolean unmute(long accountId);
}
