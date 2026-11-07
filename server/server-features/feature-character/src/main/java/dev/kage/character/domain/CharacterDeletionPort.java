package dev.kage.character.domain;

import java.time.Instant;
import java.util.Map;

/** Xoá nhân vật có chờ (ADR-205) — cột {@code character.deletion_requested_at}. Chạm DB. */
public interface CharacterDeletionPort {

    /**
     * Đánh dấu yêu cầu xoá (giữ mốc cũ nếu đã đánh dấu).
     *
     * @return {@code false} nếu nhân vật không thuộc tài khoản hoặc đã quá hạn xoá
     */
    boolean request(long accountId, long characterId, Instant now, Instant expiredBefore);

    /** Bỏ đánh dấu nếu còn trong hạn; trả trạng thái trước khi gọi. */
    State cancelIfPending(long accountId, long characterId, Instant expiredBefore);

    /** Nhân vật đang chờ xoá (chưa quá hạn) của tài khoản và mốc yêu cầu. */
    Map<Long, Instant> pendingOf(long accountId, Instant expiredBefore);

    /** Trạng thái xoá của một nhân vật. */
    enum State {
        NONE,
        PENDING,
        EXPIRED
    }
}
