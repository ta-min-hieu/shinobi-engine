package dev.kage.character.api;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import dev.kage.character.domain.CharacterDeletionPort;

/**
 * Xoá nhân vật có chờ {@link #GRACE} (ADR-205). Chạm DB — Virtual Thread. ⚠️ Danh sách / đếm nhân vật trong
 * {@code PostgresCharacterAdapter} dùng CÙNG khoảng {@link #GRACE} trong SQL — đổi ở đây phải đổi ở đó.
 */
public final class CharacterDeletionFacade {

    /** Thời gian chờ trước khi xoá hẳn; vào lại nhân vật trong khoảng này là khôi phục. */
    public static final Duration GRACE = Duration.ofDays(3);

    /** Composition không có tính năng này (test). */
    public static final CharacterDeletionFacade DISABLED = new CharacterDeletionFacade(new CharacterDeletionPort() {
        @Override
        public boolean request(long accountId, long characterId, Instant now, Instant expiredBefore) {
            return false;
        }

        @Override
        public State cancelIfPending(long accountId, long characterId, Instant expiredBefore) {
            return State.NONE;
        }

        @Override
        public Map<Long, Instant> pendingOf(long accountId, Instant expiredBefore) {
            return Map.of();
        }
    });

    private final CharacterDeletionPort port;

    /** Dựng ở composition root. */
    public CharacterDeletionFacade(CharacterDeletionPort port) {
        this.port = port;
    }

    /** Yêu cầu xoá; {@code false} nếu không phải nhân vật của tài khoản (hoặc đã quá hạn). */
    public boolean request(long accountId, long characterId, Instant now) {
        return port.request(accountId, characterId, now, now.minus(GRACE));
    }

    /** Lúc chọn nhân vật: đang chờ xoá thì khôi phục; trả trạng thái trước đó (EXPIRED = không được vào). */
    public CharacterDeletionPort.State restoreOnSelect(long accountId, long characterId, Instant now) {
        return port.cancelIfPending(accountId, characterId, now.minus(GRACE));
    }

    /** Mốc XOÁ HẲN của các nhân vật đang chờ xoá. */
    public Map<Long, Instant> deletionTimes(long accountId, Instant now) {
        Map<Long, Instant> requested = port.pendingOf(accountId, now.minus(GRACE));
        java.util.Map<Long, Instant> times = new java.util.HashMap<>();
        requested.forEach((id, at) -> times.put(id, at.plus(GRACE)));
        return times;
    }
}
