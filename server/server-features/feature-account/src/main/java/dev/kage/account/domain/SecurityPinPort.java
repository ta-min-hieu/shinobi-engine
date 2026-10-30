package dev.kage.account.domain;

import java.time.Instant;
import java.util.Optional;

/**
 * Lưu khoá an toàn (PIN cấp 2) của một tài khoản (ADR-115) — hiện thực ở server-database (V95).
 * Chỉ giữ băm + muối, KHÔNG bao giờ giữ PIN thô. Mỗi tài khoản chỉ có một phiên đăng nhập (đá phiên
 * cũ), nên hai lần thử PIN của CÙNG tài khoản không chạy song song — cổng không cần khoá dòng.
 */
public interface SecurityPinPort {

    /**
     * Bản ghi PIN — băm và muối dạng hex chữ thường.
     *
     * @param lockedUntil {@link Instant#EPOCH} nếu không bị khoá
     */
    record StoredPin(String hashHex, String saltHex, int failedAttempts, Instant lockedUntil) {}

    /** PIN của tài khoản — rỗng nếu chưa đặt. */
    Optional<StoredPin> find(long accountId);

    /** Đặt (hoặc thay) PIN, xoá đếm sai + khoá. */
    void save(long accountId, String hashHex, String saltHex, Instant at);

    /** Ghi lại số lần sai liên tiếp và mốc hết khoá ({@link Instant#EPOCH} = không khoá). */
    void recordAttempts(long accountId, int failedAttempts, Instant lockedUntil);

    /** Xoá PIN (GM, khi người chơi quên). {@code true} nếu có PIN để xoá. */
    boolean clear(long accountId);
}
