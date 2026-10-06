package dev.kage.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void shouldRejectUsername_whenTooShort() {
        assertThatThrownBy(() -> new Account(1L, "ab", Instant.now())).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUsername_whenTooLong() {
        assertThatThrownBy(() -> new Account(1L, "a".repeat(21), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectUsername_whenContainsInvalidCharacter() {
        assertThatThrownBy(() -> new Account(1L, "kage hero!", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptUsername_whenAlphanumericWithUnderscore() {
        Account account = new Account(1L, "kage_hero_1", Instant.now());

        assertThat(account.username()).isEqualTo("kage_hero_1");
    }

    /**
     * Thông điệp từ chối được gateway ghi ra log ở MỖI lượt đăng nhập sai (kèm stack trace). Nó không
     * được chép nguyên văn đầu vào của client: một khung 1 MiB thành một dòng log 1 MiB mỗi lần (40
     * lần/giây/kết nối dưới trần rate limit), và ký tự xuống dòng trong đó bịa được dòng log giả ở log
     * dạng chữ (cấu hình dev).
     */
    @Test
    void shouldKeepRejectionMessageBounded_andFreeOfClientControlCharacters() {
        assertThatThrownBy(() -> Account.validateUsername("a".repeat(1_000_000)))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).hasSizeLessThan(200));
        assertThatThrownBy(() -> Account.validateUsername("ab\nINFO forged-line"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("\n").doesNotContain("forged"));
        assertThatThrownBy(() -> Account.validateUsername("kage\u0000hero\u202E"))
                .isInstanceOf(IllegalArgumentException.class)
                .satisfies(e -> assertThat(e.getMessage())
                        .doesNotContain("\u0000")
                        .doesNotContain("\u202E")
                        .contains("U+0000"));
    }
}
