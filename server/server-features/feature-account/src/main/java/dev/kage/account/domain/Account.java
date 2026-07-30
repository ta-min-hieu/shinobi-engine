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
        Objects.requireNonNull(username, "username");
        Objects.requireNonNull(createdAt, "createdAt");
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("username phải dài %d-%d ký tự: '%s'"
                    .formatted(USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH, username));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("username chỉ được chứa chữ/số/gạch dưới: '" + username + "'");
        }
    }
}
