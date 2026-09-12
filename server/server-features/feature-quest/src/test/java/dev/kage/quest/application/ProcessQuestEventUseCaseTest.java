package dev.kage.quest.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import dev.kage.quest.domain.AuditPort;
import dev.kage.quest.domain.QuestItemTakePort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Từ 2026-09-05 lớp này KHÔNG còn hardcode một mã quest: nó chạy hook {@code on_event} của MỌI quest
 * đang làm và để chính script quyết sự kiện có liên quan không (Điều 27).
 *
 * <p><b>Fake có transaction thật (2026-09-11).</b> Từ khi có mục tiêu "mang vật X tới NPC Y", một
 * phần của hợp đồng là ROLLBACK: thiếu một món đồ thì lần ghi tiến độ và những món đã lấy trước đó
 * phải biến mất. {@code action -> action.accept(null)} không mô phỏng được điều đó, nên các Fake ở
 * đây ghi TẠM và chỉ thành thật khi {@link RecordingTx} commit — đúng như Postgres.
 */
class ProcessQuestEventUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long TEMPLATE_ID = 100L;
    private static final String TRACKED_QUEST_CODE = "kill_training_dummies";
    private static final String SCRIPT_REF = "/scripts/quest/test_fixture.js";
    /** Script mục tiêu loại THỨ HAI ({@code npc_talked}) — tiến độ là một CỜ, không phải bộ đếm. */
    private static final String NPC_SCRIPT_REF = "/scripts/quest/test_fixture_npc.js";
    /** Script mục tiêu loại THỨ BA — giao 1 {@code sealed_letter} + 2 {@code spirit_herb} cho sư tỷ. */
    private static final String DELIVERY_SCRIPT_REF = "/scripts/quest/test_fixture_delivery.js";

    private static final String NPC_QUEST_CODE = "report_to_outer_sect";
    private static final String DELIVERY_QUEST_CODE = "deliver_to_senior_sister";

    /** Một bên tham gia transaction giả: ghi tạm, chỉ thành thật khi commit. */
    interface Staged {
        void commit();

        void rollback();
    }

    /** Commit khi khối lệnh chạy xong, rollback khi nó ném — cùng hợp đồng JdbcTransactionRunner. */
    static final class RecordingTx implements TransactionRunner {

        private final List<Staged> participants;
        int commits;
        int rollbacks;

        RecordingTx(Staged... participants) {
            this.participants = List.of(participants);
        }

        @Override
        @SuppressWarnings("PMD.AvoidCatchingGenericException") // ranh giới transaction — ném lại ngay
        public void inTransaction(Consumer<Transaction> action) {
            try {
                action.accept(null);
            } catch (RuntimeException e) {
                rollbacks++;
                participants.forEach(Staged::rollback);
                throw e;
            }
            commits++;
            participants.forEach(Staged::commit);
        }
    }

    /**
     * "Bảng" tiến độ trong bộ nhớ. {@link #listInProgress} trả ảnh chụp đã dựng sẵn — y như DB thật
     * một nhịp trước khi lần ghi của sự kiện khác được đọc lại.
     */
    static final class FakeQuestProgressPort implements QuestProgressPort, Staged {

        private final List<ActiveQuest> active;
        private final Map<Long, QuestProgress> rows = new HashMap<Long, QuestProgress>();
        private final List<QuestProgress> pending = new ArrayList<QuestProgress>();
        private final ArrayDeque<QuestProgress> concurrentWrites = new ArrayDeque<QuestProgress>();
        private final List<QuestProgress> savedAll = new ArrayList<QuestProgress>();

        FakeQuestProgressPort(List<ActiveQuest> active) {
            this.active = List.copyOf(active);
            for (ActiveQuest quest : active) {
                rows.put(
                        quest.questTemplateId(),
                        new QuestProgress(
                                CHARACTER_ID, quest.questTemplateId(), QuestStatus.IN_PROGRESS, quest.progressJson()));
            }
        }

        /** Giả lập một sự kiện KHÁC đã commit ngay trước lần {@code saveIfUnchanged} kế tiếp. */
        void concurrentWriteBeforeNextSave(QuestProgress winner) {
            concurrentWrites.add(winner);
        }

        @Override
        public List<ActiveQuest> listInProgress(long characterId) {
            return active;
        }

        @Override
        public Optional<QuestProgress> find(long characterId, long questTemplateId) {
            return Optional.ofNullable(rows.get(questTemplateId));
        }

        @Override
        public void save(Transaction tx, QuestProgress progress) {
            throw new UnsupportedOperationException("dispatch phải ghi bằng saveIfUnchanged, không ghi đè mù");
        }

        @Override
        public boolean saveIfUnchanged(Transaction tx, QuestProgress progress, String expectedProgressJson) {
            QuestProgress winner = concurrentWrites.poll();
            if (winner != null) {
                rows.put(winner.questTemplateId(), winner);
            }
            QuestProgress row = rows.get(progress.questTemplateId());
            if (row == null
                    || row.status() != QuestStatus.IN_PROGRESS
                    || !row.progressJson().equals(expectedProgressJson)) {
                return false;
            }
            pending.add(progress);
            return true;
        }

        @Override
        public void commit() {
            for (QuestProgress progress : pending) {
                rows.put(progress.questTemplateId(), progress);
                savedAll.add(progress);
            }
            pending.clear();
        }

        @Override
        public void rollback() {
            pending.clear();
        }

        QuestProgress saved() {
            return savedAll.isEmpty() ? null : savedAll.get(savedAll.size() - 1);
        }
    }

    /** Túi đồ trong bộ nhớ: đếm theo mã, lấy tạm tới khi commit. */
    static final class FakeItemTakePort implements QuestItemTakePort, Staged {

        private final Map<String, Integer> held = new HashMap<String, Integer>();
        private final Map<String, Integer> pendingTaken = new HashMap<String, Integer>();
        private final Map<String, Integer> taken = new HashMap<String, Integer>();

        FakeItemTakePort holding(String itemCode, int quantity) {
            held.put(itemCode, quantity);
            return this;
        }

        @Override
        public boolean takeItemByCode(Transaction tx, long characterId, String itemTemplateCode, int quantity) {
            int available = held(itemTemplateCode) - pendingTaken.getOrDefault(itemTemplateCode, 0);
            if (available < quantity) {
                return false;
            }
            pendingTaken.merge(itemTemplateCode, quantity, Integer::sum);
            return true;
        }

        @Override
        public void commit() {
            pendingTaken.forEach((code, quantity) -> {
                held.merge(code, -quantity, Integer::sum);
                taken.merge(code, quantity, Integer::sum);
            });
            pendingTaken.clear();
        }

        @Override
        public void rollback() {
            pendingTaken.clear();
        }

        int held(String itemCode) {
            return held.getOrDefault(itemCode, 0);
        }

        int taken(String itemCode) {
            return taken.getOrDefault(itemCode, 0);
        }
    }

    static final class FakeAuditPort implements AuditPort, Staged {

        private final List<String> pending = new ArrayList<String>();
        private final List<String> actions = new ArrayList<String>();

        @Override
        public void log(Transaction tx, long characterId, String action, String detailJson) {
            pending.add(action);
        }

        @Override
        public void commit() {
            actions.addAll(pending);
            pending.clear();
        }

        @Override
        public void rollback() {
            pending.clear();
        }
    }

    /** Dựng use case với đủ ba Fake cùng tham gia MỘT transaction giả. */
    static final class Harness {

        final FakeQuestProgressPort progress;
        final FakeItemTakePort items = new FakeItemTakePort();
        final FakeAuditPort audit = new FakeAuditPort();
        final RecordingTx tx;
        final ProcessQuestEventUseCase useCase;

        Harness(QuestProgressPort.ActiveQuest... active) {
            progress = new FakeQuestProgressPort(List.of(active));
            tx = new RecordingTx(progress, items, audit);
            useCase = new ProcessQuestEventUseCase(progress, items, audit, tx);
        }
    }

    private static QuestProgressPort.ActiveQuest quest(long templateId, String code, String progressJson) {
        return new QuestProgressPort.ActiveQuest(templateId, code, SCRIPT_REF, progressJson);
    }

    private static QuestProgressPort.ActiveQuest npcQuest(long templateId, String progressJson) {
        return new QuestProgressPort.ActiveQuest(templateId, NPC_QUEST_CODE, NPC_SCRIPT_REF, progressJson);
    }

    private static QuestProgressPort.ActiveQuest deliveryQuest(String progressJson) {
        return new QuestProgressPort.ActiveQuest(TEMPLATE_ID, DELIVERY_QUEST_CODE, DELIVERY_SCRIPT_REF, progressJson);
    }

    @Test
    void shouldDoNothing_whenCharacterHasNoQuestInProgress() {
        Harness h = new Harness();

        assertThat(h.useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty();
        assertThat(h.progress.saved()).isNull();
        assertThat(h.tx.commits).isZero(); // không quest nào ⇒ không mở transaction nào
    }

    @Test
    void shouldTickProgress_butNotCompleteYet_onFirstKill() {
        Harness h = new Harness(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"));

        assertThat(h.useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty(); // fixture cần counter>=2
        assertThat(h.progress.saved().status()).isEqualTo(QuestStatus.IN_PROGRESS);
        assertThat(h.progress.saved().progressJson()).isEqualTo("{\"counter\":1}");
    }

    @Test
    void shouldCompleteAndReportStatusChange_whenThresholdReached() {
        Harness h = new Harness(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}"));

        List<ProcessQuestEventUseCase.QuestStatusChange> result =
                h.useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(result).hasSize(1);
        assertThat(result.get(0).questCode()).isEqualTo(TRACKED_QUEST_CODE);
        assertThat(result.get(0).status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(h.progress.saved().status()).isEqualTo(QuestStatus.COMPLETED);
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
        Harness h = new Harness(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"));

        assertThat(h.useCase.monsterKilled(CHARACTER_ID, "some_other_monster")).isEmpty();
        assertThat(h.progress.savedAll).isEmpty();
        assertThat(h.tx.commits).isZero(); // không mở transaction rỗng
    }

    // =============================================================================================
    // Mục tiêu loại THỨ HAI — npc_talked (2026-09-09, nhịp mở arc 2)
    // =============================================================================================

    @Test
    void shouldCompleteNpcQuest_whenTalkingToTheRightNpc() {
        Harness h = new Harness(npcQuest(TEMPLATE_ID, "{\"reported\":false}"));

        List<ProcessQuestEventUseCase.QuestStatusChange> result = h.useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(NPC_QUEST_CODE);
        assertThat(h.progress.saved().status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(h.progress.saved().progressJson()).isEqualTo("{\"reported\":true}");
    }

    @Test
    void shouldIgnoreTalkingToTheWrongNpc() {
        Harness h = new Harness(npcQuest(TEMPLATE_ID, "{\"reported\":false}"));

        assertThat(h.useCase.npcTalked(CHARACTER_ID, "town_elder")).isEmpty();
        assertThat(h.progress.savedAll).isEmpty();
    }

    /**
     * HAI LOẠI SỰ KIỆN KHÔNG ĐƯỢC LẪN VÀO NHAU. Đây là phép kiểm mà cả kiến trúc đợt này dựng ra để
     * làm được: quest đếm quái phải MÙ trước sự kiện nói chuyện, và ngược lại. Nếu bỏ
     * {@code event.type} ở đầu điều kiện trong script, một trong hai chiều sẽ đỏ ở đây.
     */
    @Test
    void shouldKeepTheTwoEventTypesApart_whenBothQuestsAreInProgress() {
        Harness h = new Harness(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}"),
                npcQuest(TEMPLATE_ID + 1, "{\"reported\":false}"));

        // Giết quái: chỉ quest đếm quái nhích (và xong), quest NPC không bị chạm tới.
        List<ProcessQuestEventUseCase.QuestStatusChange> afterKill =
                h.useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(afterKill)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRACKED_QUEST_CODE);
        assertThat(h.progress.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID);

        // Nói chuyện: chỉ quest NPC nhích. (Quest đếm quái vẫn nằm trong danh sách của fake — đúng
        // như DB thật một nhịp trước khi status COMPLETED được đọc lại.)
        List<ProcessQuestEventUseCase.QuestStatusChange> afterTalk = h.useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(afterTalk)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(NPC_QUEST_CODE);
        assertThat(h.progress.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID, TEMPLATE_ID + 1);
    }

    /**
     * Sự kiện {@code npc_talked} LẶP LẠI mỗi lần người chơi mở lời — cờ {@code reported} vì thế phải
     * đứng yên ở {@code true}. Một script viết {@code += 1} sẽ trôi số ở đây mà không ai thấy.
     */
    @Test
    void shouldStayIdempotent_whenTalkingToTheSameNpcAgain() {
        Harness h = new Harness(npcQuest(TEMPLATE_ID, "{\"reported\":true}"));

        List<ProcessQuestEventUseCase.QuestStatusChange> result = h.useCase.npcTalked(CHARACTER_ID, "senior_sister");

        // Vẫn báo COMPLETED (script gọi complete() lần nữa), nhưng tiến độ không đổi một ký tự nào.
        assertThat(result).hasSize(1);
        assertThat(h.progress.saved().progressJson()).isEqualTo("{\"reported\":true}");
    }

    /**
     * Chính điều mà lần tổng quát hoá này sinh ra để làm được: bản cũ hardcode một mã quest nên quest
     * thứ hai KHÔNG BAO GIỜ nhận được sự kiện, dù nó đang IN_PROGRESS và đang đếm đúng con quái đó.
     */
    @Test
    void shouldTickEveryQuestInProgress_notJustTheFirstOne() {
        Harness h = new Harness(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"),
                quest(TEMPLATE_ID + 1, "quest_thu_hai", "{\"counter\":0}"));

        assertThat(h.useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty(); // cả hai mới đếm 1

        assertThat(h.progress.savedAll).hasSize(2);
        assertThat(h.progress.savedAll)
                .extracting(QuestProgress::questTemplateId)
                .containsExactly(TEMPLATE_ID, TEMPLATE_ID + 1);
        assertThat(h.progress.savedAll)
                .allSatisfy(saved -> assertThat(saved.progressJson()).isEqualTo("{\"counter\":1}"));
    }

    /** Một con quái có thể là mục tiêu CUỐI của nhiều quest cùng lúc — phải báo đủ, không chỉ cái đầu. */
    @Test
    void shouldReportEveryQuestThatCompletedInTheSameKill() {
        Harness h = new Harness(
                quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":1}"),
                quest(TEMPLATE_ID + 1, "quest_thu_hai", "{\"counter\":1}"));

        List<ProcessQuestEventUseCase.QuestStatusChange> result =
                h.useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRACKED_QUEST_CODE, "quest_thu_hai");
        assertThat(result).allSatisfy(change -> assertThat(change.status()).isEqualTo(QuestStatus.COMPLETED));
    }

    // =============================================================================================
    // Ghi có điều kiện — hai sự kiện của CÙNG một người chơi chạy song song (2026-09-11)
    // =============================================================================================

    /**
     * Mất cập nhật mà ghi đè mù ({@code save}) gây ra từ trước hôm nay: hai con quái chết cùng một
     * nhịp là hai Virtual Thread, cả hai đọc {@code counter = 0}, cả hai ghi {@code counter = 1} —
     * một cú giết biến mất. Nay lần ghi thứ hai phát hiện xung đột, đọc lại, và đếm tiếp từ 1.
     */
    @Test
    void shouldRerunTheHookOnFreshProgress_whenAnotherEventWroteFirst() {
        Harness h = new Harness(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"));
        h.progress.concurrentWriteBeforeNextSave(
                new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{\"counter\":1}"));

        List<ProcessQuestEventUseCase.QuestStatusChange> result =
                h.useCase.monsterKilled(CHARACTER_ID, "training_dummy");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(TRACKED_QUEST_CODE); // 1 (của sự kiện kia) + 1 (của lần này) = 2 ⇒ xong
        assertThat(h.progress.saved().progressJson()).isEqualTo("{\"counter\":2}");
    }

    /** Xung đột liên tiếp không được thành vòng lặp vô hạn — bỏ sự kiện, không ghi gì. */
    @Test
    void shouldGiveUp_afterRepeatedConflicts() {
        Harness h = new Harness(quest(TEMPLATE_ID, TRACKED_QUEST_CODE, "{\"counter\":0}"));
        for (int counter = 10; counter < 13; counter++) {
            h.progress.concurrentWriteBeforeNextSave(new QuestProgress(
                    CHARACTER_ID, TEMPLATE_ID, QuestStatus.IN_PROGRESS, "{\"counter\":" + counter + "}"));
        }

        assertThat(h.useCase.monsterKilled(CHARACTER_ID, "training_dummy")).isEmpty();
        assertThat(h.progress.savedAll).isEmpty();
    }

    // =============================================================================================
    // Mục tiêu loại THỨ BA — "mang vật X tới NPC Y" (2026-09-11)
    // =============================================================================================

    @Test
    void shouldHandOverAndComplete_inOneTransaction_whenCarryingEverything() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":false}"));
        h.items.holding("sealed_letter", 1).holding("spirit_herb", 3);

        List<ProcessQuestEventUseCase.QuestStatusChange> result = h.useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(result)
                .extracting(ProcessQuestEventUseCase.QuestStatusChange::questCode)
                .containsExactly(DELIVERY_QUEST_CODE);
        assertThat(h.items.held("sealed_letter")).isZero();
        assertThat(h.items.held("spirit_herb")).isEqualTo(1); // lấy ĐÚNG 2, không vét sạch
        assertThat(h.progress.saved().status()).isEqualTo(QuestStatus.COMPLETED);
        assertThat(h.audit.actions)
                .containsExactly(
                        ProcessQuestEventUseCase.ITEM_TAKEN_AUDIT_ACTION,
                        ProcessQuestEventUseCase.ITEM_TAKEN_AUDIT_ACTION);
        assertThat(h.tx.commits).isEqualTo(1);
    }

    /**
     * LUẬT 1 của loại mục tiêu này: lấy đồ là ĐIỀU KIỆN của cả lần chạy hook. Phong thư đã lấy được,
     * linh thảo thì thiếu một cây — phong thư phải QUAY VỀ túi, và cờ {@code delivered} mà script đã
     * đặt không bao giờ được ghi xuống. Không có luật này thì một lần mở lời thiếu đồ âm thầm ăn mất
     * phần đồ đang có, và người chơi quay lại với đủ đồ cũng không bao giờ giao được nữa.
     */
    @Test
    void shouldLeaveNoTrace_whenOneOfTheItemsIsMissing() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":false}"));
        h.items.holding("sealed_letter", 1).holding("spirit_herb", 1);

        assertThat(h.useCase.npcTalked(CHARACTER_ID, "senior_sister")).isEmpty();

        assertThat(h.tx.rollbacks).isEqualTo(1);
        assertThat(h.items.taken("sealed_letter")).isZero();
        assertThat(h.items.held("sealed_letter")).isEqualTo(1);
        assertThat(h.progress.savedAll).isEmpty();
        assertThat(h.audit.actions).isEmpty();
    }

    /**
     * LUẬT 2: sự kiện {@code npc_talked} LẶP LẠI, nên đã giao rồi thì mở lời lần nữa không được đòi
     * đồ lần hai — tiến độ là CỜ, và điều kiện trong script có {@code !progress.delivered}. (Khi giao
     * đồ là bước CUỐI thì quest đã rời IN_PROGRESS nên không ai gọi lại; cờ này là thứ gác trường hợp
     * giao đồ chỉ là một bước giữa.)
     */
    @Test
    void shouldNotTakeAgain_onceDelivered() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":true}"));
        h.items.holding("sealed_letter", 1).holding("spirit_herb", 2);

        h.useCase.npcTalked(CHARACTER_ID, "senior_sister");

        assertThat(h.items.taken("sealed_letter")).isZero();
        assertThat(h.items.taken("spirit_herb")).isZero();
        assertThat(h.audit.actions).isEmpty();
    }

    @Test
    void shouldNotTouchTheBag_whenTalkingToSomeoneElse() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":false}"));
        h.items.holding("sealed_letter", 1).holding("spirit_herb", 2);

        assertThat(h.useCase.npcTalked(CHARACTER_ID, "town_elder")).isEmpty();

        assertThat(h.tx.commits + h.tx.rollbacks).isZero();
        assertThat(h.items.held("sealed_letter")).isEqualTo(1);
    }

    /**
     * LUẬT 3: KHÔNG giao hai lần. Người chơi bấm hai lần thật nhanh (hoặc client tự gửi lại) ⇒ hai
     * lần mở lời chạy song song, cả hai đọc {@code delivered = false}. Lần kia đã giao và đóng quest
     * trước; lần này phải nhận ra điều đó và KHÔNG lấy đồ — nếu không, đồ bị trừ hai lần cho một lần
     * giao.
     */
    @Test
    void shouldNotHandOverTwice_whenAConcurrentTalkDeliveredFirst() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":false}"));
        h.items.holding("sealed_letter", 2).holding("spirit_herb", 4);
        h.progress.concurrentWriteBeforeNextSave(
                new QuestProgress(CHARACTER_ID, TEMPLATE_ID, QuestStatus.COMPLETED, "{\"delivered\":true}"));

        assertThat(h.useCase.npcTalked(CHARACTER_ID, "senior_sister")).isEmpty();

        assertThat(h.items.taken("sealed_letter")).isZero();
        assertThat(h.items.taken("spirit_herb")).isZero();
        assertThat(h.progress.savedAll).isEmpty();
    }

    /**
     * Script ném lỗi SAU khi đã gọi {@code take_item}, {@code save_progress} và {@code complete()} —
     * không hiệu ứng dở dang nào được áp. Trước hôm nay {@code QuestScriptRunner} trả nguyên hiệu ứng
     * dở dang, trái với chính ghi chú "coi như không có hiệu ứng gì xảy ra" của nó.
     */
    @Test
    void shouldApplyNothing_whenTheScriptFailsHalfway() {
        Harness h = new Harness(deliveryQuest("{\"delivered\":false}"));
        h.items.holding("sealed_letter", 1);

        assertThat(h.useCase.npcTalked(CHARACTER_ID, "broken_npc")).isEmpty();

        assertThat(h.tx.commits + h.tx.rollbacks).isZero();
        assertThat(h.items.held("sealed_letter")).isEqualTo(1);
        assertThat(h.progress.savedAll).isEmpty();
    }
}
