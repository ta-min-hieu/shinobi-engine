package dev.kage.quest.domain;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuestTemplateTest {

    @Test
    void shouldRejectNonPositiveId() {
        assertThatThrownBy(() -> QuestTemplate.withoutPrerequisite(0, "/scripts/quest/x.js"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectBlankScriptRef() {
        assertThatThrownBy(() -> QuestTemplate.withoutPrerequisite(1, " "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
