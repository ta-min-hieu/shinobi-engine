package dev.kage.server.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** ADR-221 — NPC cần gặp đọc từ script quest thật trên classpath. */
class QuestNpcTargetsTest {

    @Test
    void shouldFindTheTalkTargetOfARealQuestScript() {
        assertThat(QuestNpcTargets.of("report_to_outer_sect")).isEqualTo("senior_sister");
        assertThat(QuestNpcTargets.of("kill_training_dummies")).isEmpty();
        assertThat(QuestNpcTargets.of("../../etc/passwd")).isEmpty();
        assertThat(QuestNpcTargets.of("no_such_quest")).isEmpty();
    }

    @Test
    void shouldTakeTheFirstNpcCheck() {
        assertThat(QuestNpcTargets.firstNpcIn("if (event.npc_code == 'town_elder' || event.npc_code === \"x\")"))
                .isEqualTo("town_elder");
        assertThat(QuestNpcTargets.firstNpcIn("event.monster_code === 'wolf'")).isEmpty();
    }
}
