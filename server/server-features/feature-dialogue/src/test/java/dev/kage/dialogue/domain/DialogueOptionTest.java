package dev.kage.dialogue.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DialogueOptionTest {

    @Test
    void shouldRejectBlankLabelKey() {
        assertThatThrownBy(() -> new DialogueOption(" ", "end")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankAction() {
        assertThatThrownBy(() -> new DialogueOption("dialogue.option.bye", " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
