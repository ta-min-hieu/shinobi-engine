package dev.kage.chat.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ChatMessageTest {

    @Test
    void shouldRejectBlankBody() {
        assertThatThrownBy(() -> new ChatMessage(1, ChatChannel.WORLD, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBodyLongerThanMax() {
        var tooLong = "a".repeat(ChatMessage.MAX_BODY_LENGTH + 1);

        assertThatThrownBy(() -> new ChatMessage(1, ChatChannel.WORLD, tooLong))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcceptBodyAtMaxLength() {
        var atMax = "a".repeat(ChatMessage.MAX_BODY_LENGTH);

        new ChatMessage(1, ChatChannel.WORLD, atMax);
    }
}
