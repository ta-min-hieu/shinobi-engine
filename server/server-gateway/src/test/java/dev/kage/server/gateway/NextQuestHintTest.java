package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.protocol.NextQuestHintKind;
import dev.kage.quest.domain.QuestCatalogPort;
import dev.kage.quest.domain.QuestStatus;

/** ADR-228 — thứ tự ưu tiên gợi ý; NPC giao đọc từ script hội thoại thật. */
class NextQuestHintTest {

    private static final List<QuestCatalogPort.Entry> CATALOG = List.of(
            new QuestCatalogPort.Entry("a", Optional.empty(), false),
            new QuestCatalogPort.Entry("daily", Optional.empty(), true),
            new QuestCatalogPort.Entry("b", Optional.of("a"), false));

    private static final Map<String, String> GIVERS = Map.of("a", "town_elder", "b", "senior_sister", "daily", "x");

    @Test
    void shouldPreferInProgress_thenClaimable_thenTheNextStartableStoryQuest() {
        Map<String, QuestStatus> log = new LinkedHashMap<>();
        assertThat(NextQuestHint.of(log, CATALOG, GIVERS).getQuestCode()).isEqualTo("a");

        log.put("a", QuestStatus.IN_PROGRESS);
        assertThat(NextQuestHint.of(log, CATALOG, GIVERS).getKind())
                .isEqualTo(NextQuestHintKind.NEXT_QUEST_HINT_KIND_CONTINUE);

        log.put("a", QuestStatus.COMPLETED);
        assertThat(NextQuestHint.of(log, CATALOG, GIVERS).getKind())
                .isEqualTo(NextQuestHintKind.NEXT_QUEST_HINT_KIND_CLAIM);

        log.put("a", QuestStatus.CLAIMED);
        assertThat(NextQuestHint.of(log, CATALOG, GIVERS)).satisfies(hint -> {
            assertThat(hint.getQuestCode()).isEqualTo("b");
            assertThat(hint.getNpcCode()).isEqualTo("senior_sister");
        });

        log.put("b", QuestStatus.CLAIMED);
        assertThat(NextQuestHint.of(log, CATALOG, GIVERS).getKind())
                .isEqualTo(NextQuestHintKind.NEXT_QUEST_HINT_KIND_NONE);
    }

    @Test
    void shouldIndexGiversFromRealDialogueScripts() {
        assertThat(QuestGivers.index(List.of("town_elder"))).containsEntry("kill_training_dummies", "town_elder");
        assertThat(QuestGivers.questsIn("action: \"accept_quest:x_y\", other: \"accept_quest:z\""))
                .containsExactly("x_y", "z");
    }
}
