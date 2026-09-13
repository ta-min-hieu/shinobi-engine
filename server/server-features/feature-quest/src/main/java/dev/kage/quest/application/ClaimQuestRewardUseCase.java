package dev.kage.quest.application;

import java.util.List;

import dev.kage.quest.domain.IdempotencyKeyReusedException;
import dev.kage.quest.domain.QuestInventoryGrantPort;
import dev.kage.quest.domain.QuestProgress;
import dev.kage.quest.domain.QuestProgressPort;
import dev.kage.quest.domain.QuestStatus;
import dev.kage.quest.domain.QuestTemplate;
import dev.kage.quest.domain.QuestTemplatePort;
import dev.kage.quest.domain.Transaction;
import dev.kage.quest.domain.TransactionRunner;

/**
 * Nhận thưởng quest ĐÃ HOÀN THÀNH (status COMPLETED) — chạy {@code on_claim} lấy EXP/item thưởng,
 * ghi {@code idempotencyKey} (ADR-025 — chống double-effect khi client tự retry) + trao thưởng
 * THẬT (EXP + item) + ghi audit + đổi status CLAIMED trong 1 transaction (DATABASE.md §5 — trao
 * thưởng thành công nhưng không đánh dấu CLAIMED = nhận thưởng lặp lại được).
 */
public final class ClaimQuestRewardUseCase {

    private final QuestProgressPort progressPort;
    private final QuestTemplatePort templatePort;
    private final QuestRewardPorts rewardPorts;
    private final TransactionRunner tx;
    private final QuestScriptRunner scriptRunner = new QuestScriptRunner();

    public ClaimQuestRewardUseCase(
            QuestProgressPort progressPort,
            QuestTemplatePort templatePort,
            QuestRewardPorts rewardPorts,
            TransactionRunner tx) {
        this.progressPort = progressPort;
        this.templatePort = templatePort;
        this.rewardPorts = rewardPorts;
        this.tx = tx;
    }

    public ClaimResult execute(long characterId, String questCode, String idempotencyKey) {
        QuestTemplate template = templatePort.findByCode(questCode).orElse(null);
        if (template == null) {
            return ClaimResult.notFound();
        }
        QuestProgress progress = progressPort.find(characterId, template.id()).orElse(null);
        if (progress == null) {
            return ClaimResult.notAccepted();
        }
        if (progress.status() != QuestStatus.COMPLETED) {
            return ClaimResult.notCompletedYet();
        }

        QuestScriptRunner.HookEffects effects = scriptRunner.runClaim(template.scriptRef(), progress.progressJson());
        if (effects.scriptFailed()) {
            // Tới 2026-09-12 nhánh này là một lỗ MẤT PHẦN THƯỞNG VĨNH VIỄN: hiệu ứng dở dang được
            // trao (những give_exp/give_item chạy được trước chỗ lỗi), status đổi sang CLAIMED, và
            // idempotency_key bị ĐỐT — nên phần thưởng còn lại không bao giờ về, mà cũng không lĩnh
            // lại được nữa. Nay dừng TRƯỚC transaction: chưa một dòng nào được ghi, quest vẫn
            // COMPLETED, key vẫn còn nguyên để lần lĩnh sau dùng lại.
            return ClaimResult.scriptError();
        }
        if (!hasRoomForRewards(characterId, effects.itemGrants())) {
            return ClaimResult.inventoryFull();
        }
        try {
            tx.inTransaction((Transaction t) -> {
                rewardPorts.idempotencyPort().recordOrThrow(t, idempotencyKey);
                rewardPorts.expPort().grantExp(t, characterId, effects.expGranted());
                for (QuestScriptRunner.ItemGrant itemGrant : effects.itemGrants()) {
                    rewardPorts
                            .inventoryGrantPort()
                            .grantItemByCode(t, characterId, itemGrant.itemTemplateCode(), itemGrant.quantity());
                }
                rewardPorts.auditPort().log(t, characterId, "QUEST_CLAIM", detailJson(questCode, effects));
                QuestProgress claimed =
                        new QuestProgress(characterId, template.id(), QuestStatus.CLAIMED, progress.progressJson());
                progressPort.save(t, claimed);
            });
        } catch (IdempotencyKeyReusedException e) {
            return ClaimResult.duplicateRequest();
        }

        List<ClaimResult.ItemReward> items = effects.itemGrants().stream()
                .map(g -> new ClaimResult.ItemReward(g.itemTemplateCode(), g.quantity()))
                .toList();
        return ClaimResult.ok(effects.expGranted(), items);
    }

    /**
     * Túi còn đủ ô cho trọn lô phần thưởng không — hỏi TRƯỚC khi ghi bất cứ thứ gì.
     *
     * <p>Túi đầy KHÔNG ném exception ({@code Inventory.add} coi đó là trạng thái bình thường,
     * adapter chỉ trả {@code false}), nên guard {@code scriptFailed} KHÔNG che được ca này. Bug đo
     * thật 2026-09-13: quest 11 khai trao 6 món, túi chỉ nhận 5, mà response vẫn OK và
     * {@code idempotency_key} vẫn bị đốt ⇒ mất món vĩnh viễn.
     *
     * <p>Mở một transaction CHỈ ĐỌC riêng, trước transaction ghi: câu hỏi này cần {@code Connection}
     * mà cách lấy duy nhất của adapter là qua {@link Transaction}. Không ghi gì ở đây nên
     * {@code idempotency_key} còn nguyên khi trả về {@code INVENTORY_FULL}.
     */
    private boolean hasRoomForRewards(long characterId, List<QuestScriptRunner.ItemGrant> itemGrants) {
        if (itemGrants.isEmpty()) {
            return true;
        }
        List<QuestInventoryGrantPort.ItemGrantRequest> requests = itemGrants.stream()
                .map(g -> new QuestInventoryGrantPort.ItemGrantRequest(g.itemTemplateCode(), g.quantity()))
                .toList();
        boolean[] roomy = {true};
        tx.inTransaction(
                (Transaction t) -> roomy[0] = rewardPorts.inventoryGrantPort().hasRoomFor(t, characterId, requests));
        return roomy[0];
    }

    private static String detailJson(String questCode, QuestScriptRunner.HookEffects effects) {
        return "{\"questCode\":\"%s\",\"expGranted\":%d,\"itemCount\":%d}"
                .formatted(questCode, effects.expGranted(), effects.itemGrants().size());
    }

    public record ClaimResult(Outcome outcome, long expGranted, List<ItemReward> itemsGranted) {
        public ClaimResult {
            itemsGranted = List.copyOf(itemsGranted);
        }

        public enum Outcome {
            OK,
            QUEST_NOT_FOUND,
            NOT_ACCEPTED,
            NOT_COMPLETED_YET,
            DUPLICATE_REQUEST,
            /** Hook {@code on_claim} ném lỗi — chưa ghi gì, quest vẫn COMPLETED và lĩnh lại được. */
            SCRIPT_ERROR,
            /** Túi không đủ ô — chưa ghi gì, quest vẫn COMPLETED và lĩnh lại được sau khi dọn túi. */
            INVENTORY_FULL
        }

        public record ItemReward(String itemTemplateCode, int quantity) {}

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

        static ClaimResult duplicateRequest() {
            return new ClaimResult(Outcome.DUPLICATE_REQUEST, 0, List.of());
        }

        static ClaimResult scriptError() {
            return new ClaimResult(Outcome.SCRIPT_ERROR, 0, List.of());
        }

        static ClaimResult inventoryFull() {
            return new ClaimResult(Outcome.INVENTORY_FULL, 0, List.of());
        }
    }
}
