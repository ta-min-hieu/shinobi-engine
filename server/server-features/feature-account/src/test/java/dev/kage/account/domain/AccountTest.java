package dev.kage.account.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class AccountTest {

    @Test
    void shouldRejectUsername_whenTooShort() {
        assertThatThrownBy(() -> new Account(1L, "ab", Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
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
        var account = new Account(1L, "kage_hero_1", Instant.now());

        assertThat(account.username()).isEqualTo("kage_hero_1");
    }
}
