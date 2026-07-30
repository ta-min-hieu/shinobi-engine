package dev.kage.dialogue.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class DialogueNodeTest {

    @Test
    void shouldRejectBlankNodeId() {
        assertThatThrownBy(() -> new DialogueNode(" ", "dialogue.greeting", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankTextKey() {
        assertThatThrownBy(() -> new DialogueNode("start", " ", List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowEmptyOptions_terminalNode() {
        DialogueNode node = new DialogueNode("start", "dialogue.greeting", List.of());

        assertThat(node.options()).isEmpty();
    }
}
