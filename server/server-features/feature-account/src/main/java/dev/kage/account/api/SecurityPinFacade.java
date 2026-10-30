package dev.kage.account.api;

import java.time.Instant;
import java.util.Optional;

import dev.kage.account.domain.SecurityPinPort;
import dev.kage.account.domain.SecurityPinRules;

/**
 * Khoá an toàn — PIN cấp 2 của tài khoản (ADR-115). Chạm DB — chỉ gọi trên Virtual Thread. {@code accountId} luôn từ
 * session, không từ gói tin.
 */
public final class SecurityPinFacade {

    /** Composition không có khoá an toàn (test) — không ai có PIN, mọi thứ mở. */
    public static final SecurityPinFacade DISABLED = new SecurityPinFacade(new SecurityPinPort() {
        @Override
        public Optional<StoredPin> find(long accountId) {
            return Optional.empty();
        }

        @Override
        public void save(long accountId, String hashHex, String saltHex, Instant at) {
            // tắt — không lưu
        }

        @Override
        public void recordAttempts(long accountId, int failedAttempts, Instant lockedUntil) {
            // tắt — không lưu
        }

        @Override
        public boolean clear(long accountId) {
            return false;
        }
    });

    /** Kết quả đặt/kiểm PIN. */
    public enum Outcome {
        OK,
        /** PIN mới không đúng dạng 4–6 chữ số. */
        INVALID_FORMAT,
        /** PIN (hiện tại) sai — đã cộng một lần sai. */
        WRONG_PIN,
        /** Sai quá số lần — đang bị khoá. */
        LOCKED,
        /** Tài khoản chưa đặt PIN (kiểm PIN). */
        NO_PIN
    }

    /**
     * Trạng thái PIN.
     *
     * @param lockedUntil {@link Instant#EPOCH} nếu không bị khoá
     */
    public record Status(boolean hasPin, Instant lockedUntil) {}

    /**
     * Kết quả kèm số lần sai còn lại trước khi bị khoá.
     *
     * @param lockedUntil {@link Instant#EPOCH} nếu không bị khoá
     */
    public record Result(Outcome outcome, int attemptsLeft, Instant lockedUntil) {}

    private final SecurityPinPort port;

    /** Dựng ở composition root. */
    public SecurityPinFacade(SecurityPinPort port) {
        this.port = port;
    }

    /** Có PIN chưa, đang khoá tới khi nào. */
    public Status status(long accountId, Instant now) {
        return port.find(accountId)
                .map(pin -> new Status(true, activeLock(pin, now)))
                .orElse(new Status(false, Instant.EPOCH));
    }

    /**
     * Đặt PIN mới. Đã có PIN thì phải đưa đúng {@code currentPin} (sai cũng tính vào lượt khoá).
     *
     * @param currentPin bỏ qua khi tài khoản chưa có PIN
     */
    public Result set(long accountId, String currentPin, String newPin, Instant now) {
        if (!SecurityPinRules.isValidFormat(newPin)) {
            return new Result(Outcome.INVALID_FORMAT, SecurityPinRules.MAX_FAILED_ATTEMPTS, Instant.EPOCH);
        }
        Optional<SecurityPinPort.StoredPin> existing = port.find(accountId);
        if (existing.isPresent()) {
            Result check = check(accountId, existing.get(), currentPin, now);
            if (check.outcome() != Outcome.OK) {
                return check;
            }
        }
        String salt = SecurityPinRules.newSaltHex();
        port.save(accountId, SecurityPinRules.hashHex(newPin, salt), salt, now);
        return new Result(Outcome.OK, SecurityPinRules.MAX_FAILED_ATTEMPTS, Instant.EPOCH);
    }

    /** Kiểm PIN để mở khoá phiên. */
    public Result verify(long accountId, String pin, Instant now) {
        return port.find(accountId)
                .map(stored -> check(accountId, stored, pin, now))
                .orElse(new Result(Outcome.NO_PIN, SecurityPinRules.MAX_FAILED_ATTEMPTS, Instant.EPOCH));
    }

    /** GM xoá PIN cho người quên. {@code true} nếu có PIN để xoá. */
    public boolean clear(long accountId) {
        return port.clear(accountId);
    }

    private Result check(long accountId, SecurityPinPort.StoredPin stored, String pin, Instant now) {
        Instant lock = activeLock(stored, now);
        if (!Instant.EPOCH.equals(lock)) {
            return new Result(Outcome.LOCKED, 0, lock);
        }
        // Khoá đã hết hạn thì đếm lại từ đầu.
        int failed = stored.lockedUntil().isAfter(Instant.EPOCH) ? 0 : stored.failedAttempts();
        if (SecurityPinRules.isValidFormat(pin) && SecurityPinRules.matches(pin, stored)) {
            if (failed > 0 || stored.lockedUntil().isAfter(Instant.EPOCH)) {
                port.recordAttempts(accountId, 0, Instant.EPOCH);
            }
            return new Result(Outcome.OK, SecurityPinRules.MAX_FAILED_ATTEMPTS, Instant.EPOCH);
        }
        int nowFailed = failed + 1;
        if (nowFailed >= SecurityPinRules.MAX_FAILED_ATTEMPTS) {
            Instant until = now.plus(SecurityPinRules.LOCK_DURATION);
            port.recordAttempts(accountId, nowFailed, until);
            return new Result(Outcome.LOCKED, 0, until);
        }
        port.recordAttempts(accountId, nowFailed, Instant.EPOCH);
        return new Result(Outcome.WRONG_PIN, SecurityPinRules.MAX_FAILED_ATTEMPTS - nowFailed, Instant.EPOCH);
    }

    private static Instant activeLock(SecurityPinPort.StoredPin pin, Instant now) {
        return pin.lockedUntil().isAfter(now) ? pin.lockedUntil() : Instant.EPOCH;
    }
}
