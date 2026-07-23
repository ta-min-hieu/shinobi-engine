package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ProcessMonsterKilledUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String TRACKED_QUEST_CODE = "kill_training_dummies";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";

    static final class FakeQuestTemplatePort implements QuestTemplatePort {
        @Override
        public Optional<QuestTemplate> findByCode(String questCode) {
            return TRACKED_QUEST_CODE.equals(questCode) ? Optional.of(new QuestTemplate(TEMPLATE_ID, SCRIPT_REF))
                    : Optional.empty();
        }
    }

    static final class FakeQuestProgressPort implements QuestProgressPort {
        private QuestProgress existing;
        QuestProgress saved;

        FakeQuestProgressPort(QuestProgress existing) {
            this.existing = existing;
        }

        @Override
        public Optional<QuestProgress> find(long characterId, long questTemplateId) {
            return Optional.ofNullable(saved != null ? saved : existing);
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            this.saved = progress;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldReturnEmpty_whenCharacterHasNoProgressRow() {
        var progressPort = new FakeQuestProgressPort(null);
        var useCase = new ProcessMonsterKilledUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "training_dummy");

        assertThat(result).isEmpty();
    }

    @Test
    void shouldTickProgress_butNotCompleteYet_onFirstKill() {
        var existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{\"counter\":0}");
        var progressPort = new FakeQuestProgressPort(existing);
        var useCase = new ProcessMonsterKilledUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "training_dummy");

        assertThat(result).isEmpty(); // chưa hoàn thành (test_fixture.js cần counter>=2)
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(progressPort.saved.progressJson()).isEqualTo("{\"counter\":1}");
    }

    @Test
    void shouldCompleteAndReturnStatusChange_whenThresholdReached() {
        var existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{\"counter\":1}");
        var progressPort = new FakeQuestProgressPort(existing);
        var useCase = new ProcessMonsterKilledUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "training_dummy");

        assertThat(result).isPresent();
        assertThat(result.get().questCode()).isEqualTo(TRACKED_QUEST_CODE);
        assertThat(result.get().status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.COMPLETED);
    }

    @Test
    void shouldReturnEmpty_whenAlreadyCompleted() {
        var existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"counter\":2}");
        var progressPort = new FakeQuestProgressPort(existing);
        var useCase = new ProcessMonsterKilledUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "training_dummy");

        assertThat(result).isEmpty();
        assertThat(progressPort.saved).isNull(); // không đụng gì khi đã COMPLETED
    }

    @Test
    void shouldIgnoreUnrelatedMonster() {
        var existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{\"counter\":0}");
        var progressPort = new FakeQuestProgressPort(existing);
        var useCase = new ProcessMonsterKilledUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "some_other_monster");

        assertThat(result).isEmpty();
        assertThat(progressPort.saved.progressJson()).isEqualTo("{\"counter\":0}"); // không tăng đếm
    }
}
