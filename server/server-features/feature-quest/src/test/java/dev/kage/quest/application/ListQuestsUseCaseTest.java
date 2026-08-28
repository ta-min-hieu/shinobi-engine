package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;

/** ADR-035 Decision-4 — nhật ký nhiệm vụ, GỒM CẢ {@code CLAIMED}. */
class ListQuestsUseCaseTest {

    private static final long CHARACTER_ID = 3L;

    private static QuestProgressPort fakePort(List<QuestLogEntry> log) {
        return new QuestProgressPort() {
            @Override
            public Optional<QuestProgress> find(long characterId, long questTemplateId) {
                return Optional.empty();
            }

            @Override
            public void save(Transaction tx, QuestProgress progress) {}

            @Override
            public List<QuestLogEntry> listLog(long characterId) {
                return characterId == CHARACTER_ID ? log : List.of();
            }
        };
    }

    @Test
    void shouldReturnQuestCode_soRewardCanBeClaimedAfterRelog() {
        ListQuestsUseCase useCase = new ListQuestsUseCase(
                fakePort(List.of(new QuestLogEntry("kill_training_dummies", QuestStatus.COMPLETED))));

        List<QuestLogEntry> log = useCase.execute(CHARACTER_ID);

        // quest_code là thứ ClaimQuestRewardCommand bắt buộc phải có; trước ADR-035 client chỉ lấy
        // được nó từ chuỗi action của hội thoại lúc bấm nhận, nên relog là mất thưởng vĩnh viễn.
        assertThat(log).containsExactly(new QuestLogEntry("kill_training_dummies", QuestStatus.COMPLETED));
    }

    @Test
    void shouldIncludeClaimedQuests() {
        ListQuestsUseCase useCase = new ListQuestsUseCase(fakePort(List.of(
                new QuestLogEntry("kill_training_dummies", QuestStatus.CLAIMED),
                new QuestLogEntry("talk_to_town_elder", QuestStatus.IN_PROGRESS))));

        List<QuestLogEntry> log = useCase.execute(CHARACTER_ID);

        // Lọc CLAIMED phía server thì client không phân biệt được "chưa từng nhận" với "đã trả xong".
        assertThat(log).extracting(QuestLogEntry::status).contains(QuestStatus.CLAIMED);
    }

    @Test
    void shouldReturnEmptyLog_forCharacterWhoNeverAcceptedAnything() {
        ListQuestsUseCase useCase = new ListQuestsUseCase(
                fakePort(List.of(new QuestLogEntry("kill_training_dummies", QuestStatus.CLAIMED))));

        assertThat(useCase.execute(CHARACTER_ID + 1)).isEmpty();
    }
}
