package dev.kage.server.database.quest.fuzz;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

import dev.kage.quest.api.QuestFacade;
import dev.kage.quest.domain.QuestStatus;

/**
 * Mô hình tham chiếu TUẦN TỰ của vòng quest một nhân vật — diễn {@link QuestFuzzSpec} bằng Java
 * thuần, không GraalJS, không DB. Bộ chạy đưa cùng một lệnh vào cả mô hình lẫn hệ thống thật rồi
 * đòi hai bên khớp: kết quả trả về của từng lệnh, và TRẠNG THÁI ĐÃ LƯU sau đó.
 *
 * <p>Mô hình chỉ biết thứ một người chơi tuần tự sẽ thấy. Lệnh chạy song song thì bộ chạy diễn mô
 * hình theo MỌI thứ tự có thể và chấp nhận bất kỳ thứ tự nào khớp — tức là đòi hệ thống thật
 * "tuyến tính hoá được", không đòi một thứ tự cụ thể.
 */
final class QuestFuzzModel {

    /** Trạng thái một quest trong mô hình. {@code kills} là số lần hạ đúng loài đã được TÍNH. */
    record QuestState(QuestStatus status, boolean asked, int kills) {}

    /** Phần thưởng một lượt lĩnh — ghi từ lần quan sát đầu, không chép từ script. */
    record Reward(long exp, Map<String, Integer> items) {
        Reward {
            items = Map.copyOf(items);
        }
    }

    /** Ảnh chụp bất biến để so hai nhánh thứ tự với nhau. */
    record Snapshot(
            Map<String, QuestState> quests,
            Map<String, Long> bag,
            long exp,
            int itemTakeAudits,
            Set<String> claimedThroughUseCase,
            Set<String> burnedKeys) {}

    private final QuestFuzzContent content;
    private final Map<String, QuestState> quests = new TreeMap<>();
    private final Map<String, Long> bag = new TreeMap<>();
    private final Set<String> claimedThroughUseCase = new HashSet<>();
    private final Set<String> burnedKeys = new HashSet<>();
    private long exp;
    private int itemTakeAudits;

    QuestFuzzModel(QuestFuzzContent content) {
        this.content = content;
    }

    QuestFuzzModel copy() {
        QuestFuzzModel copy = new QuestFuzzModel(content);
        copy.quests.putAll(quests);
        copy.bag.putAll(bag);
        copy.claimedThroughUseCase.addAll(claimedThroughUseCase);
        copy.burnedKeys.addAll(burnedKeys);
        copy.exp = exp;
        copy.itemTakeAudits = itemTakeAudits;
        return copy;
    }

    /** Lấy trọn trạng thái của {@code other} (nhánh thứ tự đã được chọn sau một loạt song song). */
    void become(QuestFuzzModel other) {
        quests.clear();
        quests.putAll(other.quests);
        bag.clear();
        bag.putAll(other.bag);
        claimedThroughUseCase.clear();
        claimedThroughUseCase.addAll(other.claimedThroughUseCase);
        burnedKeys.clear();
        burnedKeys.addAll(other.burnedKeys);
        exp = other.exp;
        itemTakeAudits = other.itemTakeAudits;
    }

    Snapshot snapshot() {
        return new Snapshot(
                Map.copyOf(quests),
                Map.copyOf(bag),
                exp,
                itemTakeAudits,
                Set.copyOf(claimedThroughUseCase),
                Set.copyOf(burnedKeys));
    }

    // ------------------------------------------------------------------ dựng trạng thái ban đầu

    void seedClaimed(String code) {
        quests.put(code, new QuestState(QuestStatus.CLAIMED, false, 0));
    }

    void addItem(String itemCode, long quantity) {
        bag.merge(itemCode, quantity, Long::sum);
        bag.values().removeIf(value -> value == 0L);
    }

    void removeAll(String itemCode) {
        bag.remove(itemCode);
    }

    void addExp(long amount) {
        exp += amount;
    }

    // ------------------------------------------------------------------ lệnh

    QuestFacade.AcceptResult accept(String code) {
        if (!content.hasQuest(code)) {
            return QuestFacade.AcceptResult.QUEST_NOT_FOUND;
        }
        if (quests.containsKey(code)) {
            return QuestFacade.AcceptResult.ALREADY_ACCEPTED;
        }
        Optional<String> prerequisite = content.prerequisiteOf(code);
        if (prerequisite.isPresent() && !statusOf(prerequisite.get()).equals(Optional.of(QuestStatus.CLAIMED))) {
            return QuestFacade.AcceptResult.PREREQUISITE_NOT_MET;
        }
        quests.put(code, new QuestState(QuestStatus.IN_PROGRESS, false, 0));
        return QuestFacade.AcceptResult.OK;
    }

    /** @return quest VỪA hoàn thành, theo thứ tự template — đúng thứ tự production báo. */
    List<String> monsterKilled(String monster) {
        exp += content.expOf(monster);
        List<String> completed = new ArrayList<>();
        for (String code : activeCodes()) {
            QuestFuzzSpec spec = content.spec(code);
            QuestState state = quests.get(code);
            if (!countsKill(spec, state, monster)) {
                continue;
            }
            int kills = state.kills() + 1;
            boolean done = spec.completesOnKill() && kills >= spec.kills();
            quests.put(
                    code, new QuestState(done ? QuestStatus.COMPLETED : QuestStatus.IN_PROGRESS, state.asked(), kills));
            if (done) {
                completed.add(code);
            }
        }
        return completed;
    }

    /** ADR-114 — @return quest VỪA hoàn thành nhờ đoàn hộ tống tới nơi (vỡ thì không đổi gì ở tầng quest). */
    List<String> escortFinished(String questCode, boolean arrived) {
        QuestState state = quests.get(questCode);
        if (!arrived
                || state == null
                || state.status() != QuestStatus.IN_PROGRESS
                || !content.spec(questCode).isEscort()) {
            return List.of();
        }
        quests.put(questCode, new QuestState(QuestStatus.COMPLETED, state.asked(), state.kills()));
        return List.of(questCode);
    }

    /** Lượt hạ quái KHÔNG đi qua quest (chỉ EXP) — nửa EXP của một lệnh hạ quái bị tách ra. */
    void monsterExpOnly(String monster) {
        exp += content.expOf(monster);
    }

    private static boolean countsKill(QuestFuzzSpec spec, QuestState state, String monster) {
        if (!spec.monster().equals(monster)) {
            return false;
        }
        if (spec.hasAskStep() && !state.asked()) {
            return false;
        }
        return spec.counterKind() != QuestFuzzSpec.CounterKind.COUNT_UNTIL_TARGET || state.kills() < spec.kills();
    }

    /** @return quest VỪA hoàn thành nhờ lần mở lời này. */
    List<String> npcTalked(String npc) {
        List<String> completed = new ArrayList<>();
        for (String code : activeCodes()) {
            QuestFuzzSpec spec = content.spec(code);
            QuestState state = quests.get(code);
            boolean asked = state.asked() || (spec.hasAskStep() && spec.askNpc().equals(npc));
            if (!reportOpens(spec, asked, state.kills(), npc)) {
                quests.put(code, new QuestState(state.status(), asked, state.kills()));
                continue;
            }
            if (!bagHolds(spec.deliveries())) {
                continue; // thiếu đồ ⇒ cả hook rollback, kể cả cờ "đã hỏi"
            }
            for (QuestFuzzSpec.ItemTake take : spec.deliveries()) {
                addItem(take.itemCode(), -take.quantity());
                itemTakeAudits++;
            }
            quests.put(code, new QuestState(QuestStatus.COMPLETED, asked, state.kills()));
            completed.add(code);
        }
        return completed;
    }

    private static boolean reportOpens(QuestFuzzSpec spec, boolean asked, int kills, String npc) {
        return spec.reportNpc().equals(npc) && (!spec.hasAskStep() || asked) && kills >= spec.kills();
    }

    private boolean bagHolds(List<QuestFuzzSpec.ItemTake> takes) {
        for (QuestFuzzSpec.ItemTake take : takes) {
            if (bag.getOrDefault(take.itemCode(), 0L) < take.quantity()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Câu trả lời một lệnh lĩnh sẽ nhận nếu túi ĐỦ chỗ — {@code OK} nghĩa là "được phép lĩnh"; bộ chạy
     * còn chấp nhận {@code INVENTORY_FULL} khi túi gần đầy.
     */
    QuestFacade.ClaimOutcome claimVerdict(String code, String key) {
        if (!content.hasQuest(code)) {
            return QuestFacade.ClaimOutcome.QUEST_NOT_FOUND;
        }
        Optional<QuestStatus> status = statusOf(code);
        if (status.isEmpty()) {
            return QuestFacade.ClaimOutcome.NOT_ACCEPTED;
        }
        if (status.get() != QuestStatus.COMPLETED) {
            return QuestFacade.ClaimOutcome.NOT_COMPLETED_YET;
        }
        return burnedKeys.contains(key) ? QuestFacade.ClaimOutcome.DUPLICATE_REQUEST : QuestFacade.ClaimOutcome.OK;
    }

    void applyClaim(String code, String key, Reward reward) {
        QuestState state = quests.get(code);
        quests.put(code, new QuestState(QuestStatus.CLAIMED, state.asked(), state.kills()));
        burnedKeys.add(key);
        claimedThroughUseCase.add(code);
        exp += reward.exp();
        reward.items().forEach((itemCode, quantity) -> addItem(itemCode, quantity));
    }

    // ------------------------------------------------------------------ đọc

    Optional<QuestStatus> statusOf(String code) {
        return Optional.ofNullable(quests.get(code)).map(QuestState::status);
    }

    Optional<QuestState> stateOf(String code) {
        return Optional.ofNullable(quests.get(code));
    }

    Map<String, QuestState> quests() {
        return Map.copyOf(quests);
    }

    Map<String, Long> bag() {
        return Map.copyOf(bag);
    }

    long exp() {
        return exp;
    }

    int itemTakeAudits() {
        return itemTakeAudits;
    }

    Set<String> claimedThroughUseCase() {
        return Set.copyOf(claimedThroughUseCase);
    }

    Set<String> burnedKeys() {
        return Set.copyOf(burnedKeys);
    }

    /** Quest đang làm, theo thứ tự template — đúng thứ tự {@code listInProgress} của production. */
    List<String> activeCodes() {
        List<String> active = new ArrayList<>();
        for (String code : content.codesInTemplateOrder()) {
            if (statusOf(code).equals(Optional.of(QuestStatus.IN_PROGRESS))) {
                active.add(code);
            }
        }
        return active;
    }

    /** Quest đầu tiên của chuỗi mà người chơi CHƯA nhận và tiên quyết đã lĩnh — rỗng khi đã hết chuỗi. */
    Optional<String> nextAcceptable() {
        for (String code : content.chain()) {
            if (quests.containsKey(code) || code.equals(QuestFuzzSpec.FIXTURE_DELIVERY.code())) {
                continue;
            }
            Optional<String> prerequisite = content.prerequisiteOf(code);
            if (prerequisite.isEmpty() || statusOf(prerequisite.get()).equals(Optional.of(QuestStatus.CLAIMED))) {
                return Optional.of(code);
            }
        }
        return Optional.empty();
    }
}
