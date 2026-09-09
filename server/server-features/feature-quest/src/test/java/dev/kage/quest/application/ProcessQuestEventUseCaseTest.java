package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Từ 2026-09-05 lớp này KHÔNG còn hardcode một mã quest: nó chạy hook {@code on_event} của MỌI quest
 * đang làm và để chính script quyết sự kiện có liên quan không (Điều 27).
 *
 * <p>Fake port vì thế cũng đổi: trả thẳng danh sách {@code ActiveQuest} thay vì cặp
 * {@code findByCode} + {@code find}. Không còn cần {@code QuestTemplatePort} — {@code scriptRef} đi
 * kèm ngay trong danh sách, đỡ một vòng tra.
 */
class ProcessQuestEventUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String TRACKED_QUEST_CODE = "kill_training_dummies";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";
    /** Script mục tiêu loại THỨ HAI ({@code npc_talked}) — tiến độ là một CỜ, không phải bộ đếm. */
    private static final String NPC_SCRIPT_REF = "/scripts/quest/test_fixture_npc.js";

    private static final String NPC_QUEST_CODE = "report_to_outer_sect";

    /** Fake trả đúng những quest ĐANG LÀM đã dựng sẵn; ghi lại mọi lần {@code save}. */
    static final class FakeQuestProgressPort implements QuestProgressPort {

        private final List<ActiveQuest> active;
        private final List<QuestProgress> savedAll = new ArrayList<QuestProgress>();

        FakeQuestProgressPort(List<ActiveQuest> active) {
            this.active = List.copyOf(active);
        }

        @Override
        public List<ActiveQuest> listInProgress(long characterId) {
            return active;
        }

        @Override
        public Optional<QuestProgress> find(long characterId, long questTemplateId) {
            throw new UnsupportedOperationException("use case không còn dùng đường này");
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            savedAll.add(progress);
        }

        QuestProgress saved() {
            return savedAll.isEmpty() ? null : savedAll.get(savedAll.size() - 1);
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    private static QuestProgressPort.ActiveQuest quest(long templateId, String code, String progressJson) {
        return new QuestProgressPort.ActiveQuest(templateId, code, SCRIPT_REF, progressJson);
    }

    private static QuestProgressPort.ActiveQuest npcQuest(long templateId, String progressJson) {
        return new QuestProgressPort.ActiveQuest(templateId, NPC_QUEST_CODE, NPC_SCRIPT_REF, progressJson);
    }

    @Test
    void shouldDoNothing_whenCharacterHasNoQuestInProgress() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(List.of());
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        assertThat(useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty();
        assertThat(progressPort.saved()).isNull(); // không quest nào ⇒ không chạm DB
    }

    @Test
    void shouldTickProgress_butNotCompleteYet_onFirstKill() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        assertThat(useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty(); // test_fixture.js cần counter>=2
        assertThat(progressPort.saved().status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(progressPort.saved().progressJson()).isEqualTo("{\"counter\":1}");
    }

    @Test
    void shouldCompleteAndReportStatusChange_whenThresholdReached() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        List<ProcessQuestEventUseCase.QuestStatusChange> result = useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).questCode()).isEqualTo(TRACKED_QUEST_CODE);
        assertThat(result.get(0).status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(progressPort.saved().status()).isEqualTo(QuestStatus.COMPLETED);
    }

    /**
     * Script tự bỏ qua sự kiện không liên quan — Java KHÔNG lọc theo {@code monster_code} (Điều 27).
     *
     * <p>⚠️ Và từ 2026-09-09 use case KHÔNG GHI GÌ CẢ trong trường hợp này: script trả lại đúng chuỗi
     * tiến độ cũ ⇒ ghi đè một giá trị y hệt là một transaction không đổi gì. Trước khi có loại sự
     * kiện thứ hai, nhánh này hiếm; nay MỌI quest đang làm nhận MỌI sự kiện nên nó là nhánh thường
     * gặp nhất, và số lần ghi rỗng sẽ tăng theo tích (số quest × số sự kiện) nếu để nguyên.
     */
    @Test
    void shouldIgnoreUnrelatedMonster_andNotWriteAtAll() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        assertThat(useCase.monsterKilled(CHARACTER_ID, "some_other_monster")).isEmpty();
        assertThat(progressPort.savedAll).isEmpty();
    }

    // =============================================================================================
    // Mục tiêu loại THỨ HAI — npc_talked (2026-09-09, nhịp mở arc 2)
    // =============================================================================================

    @Test
    void shouldCompleteNpcQuest_whenTalkingToTheRightNpc() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(npcQuest(TEMPLATE_ID, "{\"reported\":false}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        List<ProcessQuestEventUseCase.QuestStatusChange> result = useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(NPC_QUEST_CODE);
        assertThat(progressPort.saved().status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(progressPort.saved().progressJson()).isEqualTo("{\"reported\":true}");
    }

    @Test
    void shouldIgnoreTalkingToTheWrongNpc() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(npcQuest(TEMPLATE_ID, "{\"reported\":false}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        assertThat(useCase.npcTalked(CHARACTER_ID, "town_elder")).isEmpty();
        assertThat(progressPort.savedAll).isEmpty();
    }

    /**
     * HAI LOẠI SỰ KIỆN KHÔNG ĐƯỢC LẪN VÀO NHAU. Đây là phép kiểm mà cả kiến trúc đợt này dựng ra để
     * làm được: quest đếm quái phải MÙ trước sự kiện nói chuyện, và ngược lại. Nếu bỏ
     * {@code event.type} ở đầu điều kiện trong script, một trong hai chiều sẽ đỏ ở đây.
     */
    @Test
    void shouldKeepTheTwoEventTypesApart_whenBothQuestsAreInProgress() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(List.of(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}"),
                npcQuest(TEMPLATE_ID + 1, "{\"reported\":false}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        // Giết quái: chỉ quest đếm quái nhích (và xong), quest NPC không bị chạm tới.
        List<ProcessQuestEventUseCase.QuestStatusChange> afterKill =
                useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(afterKill)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRACKED_QUEST_CODE);
        assertThat(progressPort.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID);

        // Nói chuyện: chỉ quest NPC nhích. (Quest đếm quái vẫn nằm trong danh sách của fake — đúng
        // như DB thật một nhịp trước khi status COMPLETED được đọc lại.)
        List<ProcessQuestEventUseCase.QuestStatusChange> afterTalk = useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(afterTalk)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(NPC_QUEST_CODE);
        assertThat(progressPort.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID, TEMPLATE_ID + 1);
    }

    /**
     * Sự kiện {@code npc_talked} LẶP LẠI mỗi lần người chơi mở lời — cờ {@code reported} vì thế phải
     * đứng yên ở {@code true}. Một script viết {@code += 1} sẽ trôi số ở đây mà không ai thấy.
     */
    @Test
    void shouldStayIdempotent_whenTalkingToTheSameNpcAgain() {
        FakeQuestProgressPort progressPort =
                new FakeQuestProgressPort(List.of(npcQuest(TEMPLATE_ID, "{\"reported\":true}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        List<ProcessQuestEventUseCase.QuestStatusChange> result = useCase.npcTalked(CHARACTER_ID, "senior_sister");

        // Vẫn báo COMPLETED (script gọi complete() lần nữa), nhưng tiến độ không đổi một ký tự nào.
        assertThat(result).hasSize(1);
        assertThat(progressPort.saved().progressJson()).isEqualTo("{\"reported\":true}");
    }

    /**
     * Chính điều mà lần tổng quát hoá này sinh ra để làm được: bản cũ hardcode một mã quest nên quest
     * thứ hai KHÔNG BAO GIỜ nhận được sự kiện, dù nó đang IN_PROGRESS và đang đếm đúng con quái đó.
     */
    @Test
    void shouldTickEveryQuestInProgress_notJustTheFirstOne() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(List.of(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"),
                quest(TEMPLATE_ID + 1, "quest_thu_hai", "{\"counter\":0}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        assertThat(useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty(); // cả hai mới đếm 1

        assertThat(progressPort.savedAll).hasSize(2);
        assertThat(progressPort.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID, TEMPLATE_ID + 1);
        assertThat(progressPort.savedAll)
                .allSatisfy(saved -> assertThat(saved.progressJson()).isEqualTo("{\"counter\":1}"));
    }

    /** Một con quái có thể là mục tiêu CUỐI của nhiều quest cùng lúc — phải báo đủ, không chỉ cái đầu. */
    @Test
    void shouldReportEveryQuestThatCompletedInTheSameKill() {
        FakeQuestProgressPort progressPort = new FakeQuestProgressPort(List.of(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}"),
                quest(TEMPLATE_ID + 1, "quest_thu_hai", "{\"counter\":1}")));
        ProcessQuestEventUseCase useCase = new ProcessQuestEventUseCase(progressPort, DIRECT_TX);

        List<ProcessQuestEventUseCase.QuestStatusChange> result = useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRACKED_QUEST_CODE, "quest_thu_hai");
        assertThat(result).allSatisfy(change -> assertThat(change.status()).isEqualTo(QuestStatus.COMPLETED));
    }
}
