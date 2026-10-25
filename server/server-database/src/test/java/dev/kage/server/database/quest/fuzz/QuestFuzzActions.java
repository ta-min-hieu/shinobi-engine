package dev.kage.server.database.quest.fuzz;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.kage.dialogue.api.DialogueFacade;
import dev.kage.dialogue.domain.DialogueNode;
import dev.kage.quest.api.QuestFacade;

/**
 * Hai nửa của mỗi lệnh: {@link #execute} chạy nó trên hệ thống THẬT và ghi lại thứ quan sát được;
 * {@link #applyToModel} đưa đúng lệnh đó (cùng kết quả quan sát được) vào mô hình và nói nó có hợp
 * lệ với mô hình không. Tách đôi để một loạt lệnh song song chạy xong rồi mới đi tìm thứ tự tuần tự
 * giải thích được nó.
 */
final class QuestFuzzActions {

    /** Thứ một lệnh để lại. {@code result} là tên enum kết quả, hoặc nhãn của lối ra hội thoại. */
    record Observed(
            List<String> completed, String acceptedCode, String result, Optional<QuestFuzzModel.Reward> reward) {
        Observed {
            completed = List.copyOf(completed);
        }

        @Override
        public String toString() {
            return result
                    + (acceptedCode.isEmpty() ? "" : "(" + acceptedCode + ")")
                    + (completed.isEmpty() ? "" : " xong " + completed)
                    + reward.map(r -> " thưởng " + r).orElse("");
        }
    }

    private static final String NO_NPC = "NO_NPC";
    private static final String NPC_FOUND = "NPC_FOUND";

    /** Phần thưởng quan sát lần đầu theo quest — chung cho MỌI seed, để mọi lần lĩnh sau đối chiếu. */
    private static final ConcurrentMap<String, QuestFuzzModel.Reward> KNOWN_REWARDS = new ConcurrentHashMap<>();

    private final QuestFuzzContent content;
    private final long characterId;
    private final List<String> violations = new CopyOnWriteArrayList<>();
    private volatile QuestFuzzSystem system;

    QuestFuzzActions(QuestFuzzContent content, long characterId, QuestFuzzSystem system) {
        this.content = content;
        this.characterId = characterId;
        this.system = system;
    }

    void relog(QuestFuzzSystem fresh) {
        this.system = fresh;
    }

    QuestFuzzSystem system() {
        return system;
    }

    /** Vi phạm phát hiện ngay lúc chạy (không cần mô hình) — vd phần thưởng lệch giữa hai lần lĩnh. */
    List<String> drainViolations() {
        List<String> copy = List.copyOf(violations);
        violations.clear();
        return copy;
    }

    // ------------------------------------------------------------------ hệ thống thật

    Observed execute(QuestFuzzOp op) {
        return switch (op) {
            case QuestFuzzOp.Kill kill -> executeKill(kill);
            case QuestFuzzOp.Talk talk -> executeTalk(talk.npc());
            case QuestFuzzOp.Accept accept ->
                new Observed(
                        List.of(),
                        accept.questCode(),
                        system.quest()
                                .acceptQuest(characterId, accept.questCode())
                                .name(),
                        Optional.empty());
            case QuestFuzzOp.Dialogue dialogue -> executeDialogue(dialogue);
            case QuestFuzzOp.Claim claim -> executeClaim(claim);
            case QuestFuzzOp.Escort escort ->
                new Observed(
                        codes(system.quest().onEscortFinished(characterId, escort.questCode(), escort.arrived())),
                        escort.questCode(),
                        "",
                        Optional.empty());
        };
    }

    private Observed executeKill(QuestFuzzOp.Kill kill) {
        List<String> completed = codes(system.quest().onMonsterKilled(characterId, kill.monster()));
        long exp = content.expOf(kill.monster());
        if (exp > 0) {
            system.experience().execute(characterId, exp);
        }
        return new Observed(completed, "", "KILLED", Optional.empty());
    }

    private Observed executeTalk(String npc) {
        if (system.dialogue().startDialogue(npc).isEmpty()) {
            return new Observed(List.of(), "", NO_NPC, Optional.empty());
        }
        return new Observed(codes(system.quest().onNpcTalked(characterId, npc)), "", NPC_FOUND, Optional.empty());
    }

    private Observed executeDialogue(QuestFuzzOp.Dialogue dialogue) {
        Optional<DialogueNode> start = system.dialogue().startDialogue(dialogue.npc());
        if (start.isEmpty()) {
            return new Observed(List.of(), "", NO_NPC, Optional.empty());
        }
        List<String> completed = codes(system.quest().onNpcTalked(characterId, dialogue.npc()));
        String nodeId = start.get().nodeId();
        for (int optionIndex : dialogue.optionIndexes()) {
            DialogueFacade.SelectOptionResult result =
                    system.dialogue().selectOption(dialogue.npc(), nodeId, optionIndex);
            switch (result) {
                case DialogueFacade.SelectOptionResult.Node next ->
                    nodeId = next.node().nodeId();
                case DialogueFacade.SelectOptionResult.AcceptQuest accept -> {
                    String outcome = system.quest()
                            .acceptQuest(characterId, accept.questCode())
                            .name();
                    return new Observed(completed, accept.questCode(), outcome, Optional.empty());
                }
                default -> {
                    return new Observed(completed, "", result.getClass().getSimpleName(), Optional.empty());
                }
            }
        }
        return new Observed(completed, "", "WALKED", Optional.empty());
    }

    private Observed executeClaim(QuestFuzzOp.Claim claim) {
        QuestFacade.ClaimResult result =
                system.quest().claimReward(characterId, claim.questCode(), claim.idempotencyKey());
        if (result.outcome() != QuestFacade.ClaimOutcome.OK) {
            if (result.expGranted() != 0 || !result.itemsGranted().isEmpty()) {
                violations.add("lĩnh %s bị từ chối (%s) mà vẫn báo thưởng %s"
                        .formatted(claim.questCode(), result.outcome(), result));
            }
            return new Observed(List.of(), "", result.outcome().name(), Optional.empty());
        }
        // Đúng thứ gateway làm ngay sau một lượt lĩnh OK: EXP thưởng đã vào thẳng character.exp,
        // giờ hoà giải cột level qua use case lên cấp (CharacterFacade.syncExperienceAfterQuestReward).
        system.experience().syncAfterExternalGrant(characterId);
        Map<String, Integer> items = new TreeMap<>();
        for (QuestFacade.ItemReward item : result.itemsGranted()) {
            items.merge(item.itemTemplateCode(), item.quantity(), Integer::sum);
        }
        QuestFuzzModel.Reward reward = new QuestFuzzModel.Reward(result.expGranted(), items);
        QuestFuzzModel.Reward first = KNOWN_REWARDS.putIfAbsent(claim.questCode(), reward);
        if (first != null && !first.equals(reward)) {
            violations.add("%s: phần thưởng lần này %s ≠ lần trước %s".formatted(claim.questCode(), reward, first));
        }
        return new Observed(List.of(), "", QuestFacade.ClaimOutcome.OK.name(), Optional.of(reward));
    }

    private static List<String> codes(List<QuestFacade.QuestStatusChange> changes) {
        return changes.stream().map(QuestFacade.QuestStatusChange::questCode).toList();
    }

    // ------------------------------------------------------------------ mô hình

    /**
     * Đưa lệnh vào {@code model} với kết quả đã quan sát; rỗng nếu hợp lệ, ngược lại là lý do.
     *
     * @param tightBag túi có thể đã gần đầy lúc lệnh chạy — khi đó {@code INVENTORY_FULL} là câu trả
     *     lời hợp lệ cho một lượt lĩnh, ngoài ra thì không
     */
    Optional<String> applyToModel(QuestFuzzModel model, QuestFuzzOp op, Observed observed, boolean tightBag) {
        return switch (op) {
            case QuestFuzzOp.Kill kill -> expectCompleted(model.monsterKilled(kill.monster()), observed);
            case QuestFuzzOp.Talk talk -> applyTalk(model, talk.npc(), observed);
            case QuestFuzzOp.Accept accept ->
                expectResult(model.accept(accept.questCode()).name(), observed);
            case QuestFuzzOp.Dialogue dialogue -> applyDialogue(model, dialogue, observed);
            case QuestFuzzOp.Claim claim -> applyClaim(model, claim, observed, tightBag);
            case QuestFuzzOp.Escort escort ->
                expectCompleted(model.escortFinished(escort.questCode(), escort.arrived()), observed);
        };
    }

    private Optional<String> applyTalk(QuestFuzzModel model, String npc, Observed observed) {
        boolean known = content.npcs().contains(npc);
        if (!known) {
            return NO_NPC.equals(observed.result()) && observed.completed().isEmpty()
                    ? Optional.empty()
                    : Optional.of("NPC không tồn tại '" + npc + "' mà vẫn có hội thoại/tiến độ: " + observed);
        }
        if (NO_NPC.equals(observed.result())) {
            return Optional.of("NPC '" + npc + "' có trong DB mà không mở được hội thoại");
        }
        return expectCompleted(model.npcTalked(npc), observed);
    }

    private Optional<String> applyDialogue(QuestFuzzModel model, QuestFuzzOp.Dialogue dialogue, Observed observed) {
        Optional<String> talk = applyTalk(model, dialogue.npc(), observed);
        if (talk.isPresent() || observed.acceptedCode().isEmpty()) {
            return talk;
        }
        if (!content.hasQuest(observed.acceptedCode())) {
            return Optional.of("hội thoại của " + dialogue.npc() + " mời quest không tồn tại: " + observed);
        }
        return expectResult(model.accept(observed.acceptedCode()).name(), observed);
    }

    private static Optional<String> applyClaim(
            QuestFuzzModel model, QuestFuzzOp.Claim claim, Observed observed, boolean tightBag) {
        QuestFacade.ClaimOutcome verdict = model.claimVerdict(claim.questCode(), claim.idempotencyKey());
        String result = observed.result();
        boolean roomRefusal = tightBag
                && QuestFacade.ClaimOutcome.INVENTORY_FULL.name().equals(result)
                && (verdict == QuestFacade.ClaimOutcome.OK || verdict == QuestFacade.ClaimOutcome.DUPLICATE_REQUEST);
        if (roomRefusal) {
            return Optional.empty();
        }
        if (!verdict.name().equals(result)) {
            return Optional.of("lĩnh %s: mô hình nói %s, thật trả %s".formatted(claim.questCode(), verdict, observed));
        }
        if (verdict == QuestFacade.ClaimOutcome.OK) {
            model.applyClaim(
                    claim.questCode(), claim.idempotencyKey(), observed.reward().orElseThrow());
        }
        return Optional.empty();
    }

    private static Optional<String> expectCompleted(List<String> expected, Observed observed) {
        return expected.equals(observed.completed())
                ? Optional.empty()
                : Optional.of("mô hình báo xong " + expected + ", thật báo " + observed);
    }

    private static Optional<String> expectResult(String expected, Observed observed) {
        return expected.equals(observed.result())
                ? Optional.empty()
                : Optional.of("mô hình trả " + expected + ", thật trả " + observed);
    }
}
