package dev.kage.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate root — một tài khoản. Validate trong compact constructor (Fail Fast, CODING-STANDARDS
 * §6): mọi {@code Account} tồn tại trong hệ thống, kể cả đọc lại từ DB, đều hợp lệ theo quy tắc
 * này — không cần validate lại ở nơi dùng.
 */
public record Account(long id, String username, Instant createdAt) {

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 20;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public Account {
        Objects.requireNonNull(createdAt, "createdAt");
        validateUsername(username);
    }

    /**
     * Validate username ĐỘC LẬP với việc dựng {@code Account} — {@code LoginOrCreateAccountUseCase}
     * gọi nó TRƯỚC khi mở transaction (cùng khuôn {@code Character#validateName}). Trước 2026-10-02
     * luật chỉ chạy lúc adapter dựng lại {@code Account} SAU câu INSERT, nên username dài hơn cột
     * {@code account.username VARCHAR(20)} bị DB từ chối trước và client nhận 500
     * {@code error.login_failed} thay vì {@code error.invalid_username}.
     *
     * @throws IllegalArgumentException nếu sai độ dài hoặc chứa ký tự ngoài chữ/số/gạch dưới
     */
    public static void validateUsername(String username) {
        Objects.requireNonNull(username, "username");
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("username phải dài %d-%d ký tự: '%s'"
                    .formatted(USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH, username));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("username chỉ được chứa chữ/số/gạch dưới: '" + username + "'");
        }
    }
}
