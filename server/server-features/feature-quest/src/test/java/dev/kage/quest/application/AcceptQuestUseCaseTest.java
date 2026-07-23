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

class AcceptQuestUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String QUEST_CODE = "known_quest";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";

    static final class FakeQuestTemplatePort implements QuestTemplatePort {
        @Override
        public Optional<QuestTemplate> findByCode(String questCode) {
            return QUEST_CODE.equals(questCode) ? Optional.of(new QuestTemplate(TEMPLATE_ID, SCRIPT_REF))
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
            return Optional.ofNullable(existing);
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            this.saved = progress;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldReturnQuestNotFound_whenCodeUnknown() {
        var useCase = new AcceptQuestUseCase(new FakeQuestProgressPort(null), new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, "unknown_code");

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.QUEST_NOT_FOUND);
    }

    @Test
    void shouldReturnAlreadyAccepted_whenProgressRowExists() {
        var existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{}");
        var progressPort = new FakeQuestProgressPort(existing);
        var useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
        assertThat(progressPort.saved).isNull(); // không ghi đè tiến độ đã có
    }

    @Test
    void shouldSaveInitialProgress_whenAccepted() {
        var progressPort = new FakeQuestProgressPort(null);
        var useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        var result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        assertThat(progressPort.saved).isNotNull();
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(progressPort.saved.progressJson()).isEqualTo("{\"counter\":0}");
    }
}
