package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

class AcceptQuestUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String QUEST_CODE = "known_quest";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";
    private static final String BROKEN_QUEST_CODE = "broken_quest";
    private static final String BROKEN_SCRIPT_REF = "/scripts/quest/test_fixture_broken.js";

    static final class FakeQuestTemplatePort implements QuestTemplatePort {
        @Override
        public Optional<QuestTemplate> findByCode(String questCode) {
            if (QUEST_CODE.equals(questCode)) {
                return Optional.of(QuestTemplate.withoutPrerequisite(TEMPLATE_ID, SCRIPT_REF));
            }
            if (BROKEN_QUEST_CODE.equals(questCode)) {
                // Cùng TEMPLATE_ID: Fake progress port tra theo nhân vật nên hai mã dùng chung một dòng tiến độ.
                return Optional.of(QuestTemplate.withoutPrerequisite(TEMPLATE_ID, BROKEN_SCRIPT_REF));
            }
            return Optional.empty();
        }
    }

    static final class FakeQuestProgressPort implements QuestProgressPort {
        private QuestProgress existing;
        QuestProgress saved;
        boolean rowAppearedMeanwhile;

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

        /** {@code rowAppearedMeanwhile}: một lệnh nhận khác đã chèn dòng sau lần {@code find}. */
        @Override
        public boolean insertIfAbsent(Transaction tx, QuestProgress progress) {
            if (existing != null || rowAppearedMeanwhile) {
                return false;
            }
            this.saved = progress;
            return true;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldReturnQuestNotFound_whenCodeUnknown() {
        AcceptQuestUseCase useCase =
                new AcceptQuestUseCase(new FakeQuestProgressPort(null), new FakeQuestTemplatePort(), DIRECT_TX);

        AcceptQuestUseCase.AcceptResult result = useCase.execute(CHARACTER_ID, "unknown_code");

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.QUEST_NOT_FOUND);
    }

    @Test
    void shouldReturnAlreadyAccepted_whenProgressRowExists() {
        QuestProgress existing = new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{}");
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(existing);
        AcceptQuestUseCase useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        AcceptQuestUseCase.AcceptResult result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
        assertThat(progressPort.saved).isNull(); // không ghi đè tiến độ đã có
    }

    @Test
    void shouldSaveInitialProgress_whenAccepted() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(null);
        AcceptQuestUseCase useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        AcceptQuestUseCase.AcceptResult result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.OK);
        assertThat(progressPort.saved).isNotNull();
        assertThat(progressPort.saved.status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(progressPort.saved.progressJson()).isEqualTo("{\"counter\":0}");
    }

    /**
     * Một lệnh nhận khác chèn dòng SAU lần {@code find} của lệnh này (bấm đúp — hai lệnh chạy song
     * song ở gateway). Bản cũ upsert mù và ghi đè cả quest đã CLAIMED; nay khoá chính phán quyết.
     */
    @Test
    void shouldReturnAlreadyAccepted_whenAConcurrentAcceptInsertedFirst() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(null);
        progressPort.rowAppearedMeanwhile = true;
        AcceptQuestUseCase useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        AcceptQuestUseCase.AcceptResult result = useCase.execute(CHARACTER_ID, QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.ALREADY_ACCEPTED);
        assertThat(progressPort.saved).isNull();
    }

    /**
     * Script {@code on_accept} ném lỗi ⇒ KHÔNG ghi gì (2026-09-12).
     *
     * <p>Trước bản sửa, nhánh này ghi một dòng {@code IN_PROGRESS} mang tiến độ dở dang mà không hook
     * nào đẩy đi được nữa — {@code on_event} của chính script hỏng đó cũng lỗi, còn
     * {@code ALREADY_ACCEPTED} thì chặn nhận lại ⇒ quest chết vĩnh viễn.
     */
    @Test
    void shouldSaveNothingAndReportScriptError_whenAcceptHookThrows() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(null);
        AcceptQuestUseCase useCase = new AcceptQuestUseCase(progressPort, new FakeQuestTemplatePort(), DIRECT_TX);

        AcceptQuestUseCase.AcceptResult result = useCase.execute(CHARACTER_ID, BROKEN_QUEST_CODE);

        assertThat(result).isEqualTo(AcceptQuestUseCase.AcceptResult.SCRIPT_ERROR);
        // Fixture ĐÃ gọi save_progress trước chỗ lỗi — dòng này đỏ nghĩa là hiệu ứng dở dang lọt xuống DB.
        assertThat(progressPort.saved).isNull();
    }
}
