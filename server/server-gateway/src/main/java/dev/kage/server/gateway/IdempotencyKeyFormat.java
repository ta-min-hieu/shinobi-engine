package dev.kage.server.gateway;

import java.util.UUID;

/**
 * Định dạng {@code idempotencyKey} trên dây (ADR-025: client sinh UUID, giữ nguyên khi tự retry).
 *
 * <p>Dùng đúng {@link UUID#fromString} — cùng hàm mà các adapter idempotency của
 * {@code server-database} gọi — nên "qua được ở đây" nghĩa đúng là "adapter không nổ".
 */
final class IdempotencyKeyFormat {

    private IdempotencyKeyFormat() {}

    static boolean isWellFormed(String idempotencyKey) {
        try {
            UUID.fromString(idempotencyKey);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
