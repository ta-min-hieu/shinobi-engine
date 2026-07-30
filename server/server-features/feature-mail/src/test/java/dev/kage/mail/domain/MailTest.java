package dev.kage.mail.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class MailTest {

    @Test
    void shouldRejectNegativeGoldAmount() {
        assertThatThrownBy(() -> new Mail(1, null, 5, "s", "b", -1, List.of(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveRecipientId() {
        assertThatThrownBy(() -> new Mail(1, null, 0, "s", "b", 0, List.of(), null, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void isClaimedShouldBeFalse_whenClaimedAtIsNull() {
        Mail mail = new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now());

        assertThat(mail.isClaimed()).isFalse();
    }

    @Test
    void isClaimedShouldBeTrue_whenClaimedAtIsSet() {
        Mail mail = new Mail(1, null, 5, "s", "b", 0, List.of(), Instant.now(), Instant.now());

        assertThat(mail.isClaimed()).isTrue();
    }

    @Test
    void hasRewardShouldBeTrue_whenGoldPresent() {
        Mail mail = new Mail(1, null, 5, "s", "b", 10, List.of(), null, Instant.now());

        assertThat(mail.hasReward()).isTrue();
    }

    @Test
    void hasRewardShouldBeTrue_whenAttachmentPresent() {
        Mail mail = new Mail(1, null, 5, "s", "b", 0, List.of(new MailAttachment(1, 1)), null, Instant.now());

        assertThat(mail.hasReward()).isTrue();
    }

    @Test
    void hasRewardShouldBeFalse_whenNoGoldOrAttachment() {
        Mail mail = new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now());

        assertThat(mail.hasReward()).isFalse();
    }

    @Test
    void senderIdShouldAllowNull_forSystemMail() {
        Mail mail = new Mail(1, null, 5, "s", "b", 0, List.of(), null, Instant.now());

        assertThat(mail.senderId()).isNull();
    }
}
