package dev.kage.quest.application;

import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;
import java.util.List;

/**
 * Nhận thưởng quest ĐÃ HOÀN THÀNH (status COMPLETED) — chạy {@code on_claim} lấy EXP/item thưởng,
 * trao thưởng THẬT (EXP + item) + ghi audit + đổi status CLAIMED trong 1 transaction (DATABASE.md
 * §5 — trao thưởng thành công nhưng không đánh dấu CLAIMED = nhận thưởng lặp lại được).
 */
public final class ClaimQuestRewardUseCase {

    private final QuestProgressPort progressPort;
    private final QuestTemplatePort templatePort;
    private final QuestRewardPorts rewardPorts;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ClaimQuestRewardUseCase(
            QuestProgressPort progressPort, QuestTemplatePort templatePort, QuestRewardPorts rewardPorts,
            TransactionRunner tx) {
        this.progressPort = progressPort;
        this.templatePort = templatePort;
        this.rewardPorts = rewardPorts;
        this.tx = tx;
    }

    public ClaimResult execute(long characterId, String questCode) {
        var template = templatePort.findByCode(questCode).orElse(null);
        if (template == null) {
            return ClaimResult.notFound();
        }
        var progress = progressPort.find(characterId, template.id()).orElse(null);
        if (progress == null) {
            return ClaimResult.notAccepted();
        }
        if (progress.status() != QuestStatus.COMPLETED) {
            return ClaimResult.notCompletedYet();
        }

        var effects = scriptRunner.runClaim(template.scriptRef(), progress.progressJson());
        tx.inTransaction((Transaction t) -> {
            rewardPorts.expPort().grantExp(t, characterId, effects.expGranted());
            for (var itemGrant : effects.itemGrants()) {
                rewardPorts.inventoryGrantPort().grantItemByCode(
                        t, characterId, itemGrant.itemTemplateCode(), itemGrant.quantity());
            }
            rewardPorts.auditPort().log(t, characterId, "QUEST_CLAIM", detailJson(questCode, effects));
            var claimed = new QuestProgress(characterId, template.id(), QuestStatus.CLAIMED, progress.progressJson());
            progressPort.save(t, claimed);
        });

        var items = effects.itemGrants().stream()
                .map(g -> new ClaimResult.ItemReward(g.itemTemplateCode(), g.quantity()))
                .toList();
        return ClaimResult.ok(effects.expGranted(), items);
    }

    private static String detailJson(String questCode, QuestScriptRunner.HookEffects effects) {
        return "{\"questCode\":\"%s\",\"expGranted\":%d,\"itemCount\":%d}"
                .formatted(questCode, effects.expGranted(), effects.itemGrants().size());
    }

    public record ClaimResult(Outcome outcome, long expGranted, List<ItemReward> itemsGranted) {
        public enum Outcome { OK, QUEST_NOT_FOUND, NOT_ACCEPTED, NOT_COMPLETED_YET }

        public record ItemReward(String itemTemplateCode, int quantity) {
        }

        static ClaimResult ok(long expGranted, List<ItemReward> items) {
            return new ClaimResult(Outcome.OK, expGranted, items);
        }

        static ClaimResult notFound() {
            return new ClaimResult(Outcome.QUEST_NOT_FOUND, 0, List.of());
        }

        static ClaimResult notAccepted() {
            return new ClaimResult(Outcome.NOT_ACCEPTED, 0, List.of());
        }

        static ClaimResult notCompletedYet() {
            return new ClaimResult(Outcome.NOT_COMPLETED_YET, 0, List.of());
        }
    }
}
