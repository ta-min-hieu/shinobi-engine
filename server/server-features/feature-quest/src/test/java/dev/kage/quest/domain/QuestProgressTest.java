package dev.kage.quest.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class QuestProgressTest {

    @Test
    void shouldRejectNonPositiveCharacterId() {
        assertThatThrownBy(() -> new QuestProgress(0, 1, QuestStatus.IN_PROGRESS, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNonPositiveQuestTemplateId() {
        assertThatThrownBy(() -> new QuestProgress(1, 0, QuestStatus.IN_PROGRESS, "{}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullProgressJson() {
        assertThatThrownBy(() -> new QuestProgress(1, 1, QuestStatus.IN_PROGRESS, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAllowEmptyProgressJson() {
        assertThatCode(() -> new QuestProgress(1, 1, QuestStatus.IN_PROGRESS, "")).doesNotThrowAnyException();
    }
}
