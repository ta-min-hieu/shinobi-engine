package dev.kage.account.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Aggregate root — một tài khoản. Validate trong compact constructor (Fail Fast, CODING-STANDARDS
 * §6): mọi {@code Account} tồn tại trong hệ thống, kể cả đọc lại từ DB, đều hợp lệ theo quy tắc
 * này — không cần validate lại ở nơi dùng.
 */
public record Account(long id, String username, Instant createdAt, AccountStatus status) {

    private static final int USERNAME_MIN_LENGTH = 3;
    private static final int USERNAME_MAX_LENGTH = 20;
    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]+$");

    public Account {
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(status, "status");
        validateUsername(username);
    }

    /** Tài khoản đang hoạt động — dùng khi tạo mới và ở test không quan tâm trạng thái. */
    public Account(long id, String username, Instant createdAt) {
        this(id, username, createdAt, AccountStatus.ACTIVE);
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
        // Thông điệp KHÔNG chép nguyên văn username (sửa 2026-10-06): gateway ghi nó ra log ở mỗi lượt
        // đăng nhập sai, và username đến thẳng từ client — khung 1 MiB thành dòng log 1 MiB, ký tự xuống
        // dòng bịa được dòng log giả. Chỉ ghi độ dài và mã của ký tự sai đầu tiên.
        if (username.length() < USERNAME_MIN_LENGTH || username.length() > USERNAME_MAX_LENGTH) {
            throw new IllegalArgumentException("username phải dài %d-%d ký tự, nhận %d"
                    .formatted(USERNAME_MIN_LENGTH, USERNAME_MAX_LENGTH, username.length()));
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException(
                    "username chỉ được chứa chữ/số/gạch dưới, sai ở " + firstInvalidCharacter(username));
        }
    }

    /** {@code U+XXXX tại vị trí i} của ký tự đầu tiên ngoài {@code [a-zA-Z0-9_]} — chỉ gọi khi đã sai. */
    private static String firstInvalidCharacter(String username) {
        int index = 0;
        while (index < username.length()) {
            int codePoint = username.codePointAt(index);
            if (!isUsernameCharacter(codePoint)) {
                return "U+%04X tại vị trí %d".formatted(codePoint, index);
            }
            index += Character.charCount(codePoint);
        }
        return "không xác định";
    }

    private static boolean isUsernameCharacter(int codePoint) {
        return (codePoint >= 'a' && codePoint <= 'z')
                || (codePoint >= 'A' && codePoint <= 'Z')
                || (codePoint >= '0' && codePoint <= '9')
                || codePoint == '_';
    }
}
