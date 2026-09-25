package dev.kage.quest.application;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
        return claimInTransaction(new ClaimRequest(characterId, template.id(), questCode, idempotencyKey), effects);
    }

    /**
     * Phần ghi của lượt lĩnh — một transaction duy nhất; mọi nhánh từ chối trả về khi CHƯA ghi gì.
     */
    private ClaimResult claimInTransaction(ClaimRequest request, QuestScriptRunner.HookEffects effects) {
        long characterId = request.characterId();
        // Câu kiểm COMPLETED ở trên đọc NGOÀI transaction — chỉ để trả lời nhanh, không bảo vệ gì.
        // idempotency_key chỉ chặn lần gửi LẠI của cùng một lệnh; hai lần bấm là hai key, và bản cũ
        // trao thưởng cho MỌI lệnh lĩnh chồng nhau (đo thật: 8 lệnh đồng thời = 8 lần EXP). Thứ tự
        // trong transaction (DATABASE.md §5.1): khoá túi đồ ĐẦU TIÊN, hỏi "đủ chỗ" (chưa ghi gì),
        // rồi đổi COMPLETED -> CLAIMED có điều kiện — chỉ một giao dịch thắng, các giao dịch khác
        // nhận 0 dòng và không ghi gì cả.
        AtomicBoolean bagHasRoom = new AtomicBoolean(true);
        AtomicBoolean lostTheClaim = new AtomicBoolean(false);
        try {
            tx.inTransaction((Transaction t) -> {
                rewardPorts.inventoryGrantPort().lockInventory(t, characterId);
                if (!hasRoomForRewards(t, characterId, effects.itemGrants())) {
                    bagHasRoom.set(false);
                    return; // chưa ghi gì: quest vẫn COMPLETED, idempotency_key vẫn còn nguyên
                }
                if (!progressPort.markClaimedIfCompleted(t, characterId, request.questId())) {
                    lostTheClaim.set(true);
                    return;
                }
                rewardPorts.idempotencyPort().recordOrThrow(t, request.idempotencyKey());
                grantRewards(t, characterId, effects);
                rewardPorts.auditPort().log(t, characterId, "QUEST_CLAIM", detailJson(request.questCode(), effects));
            });
        } catch (IdempotencyKeyReusedException e) {
            return ClaimResult.duplicateRequest();
        }
        if (!bagHasRoom.get()) {
            return ClaimResult.inventoryFull();
        }
        if (lostTheClaim.get()) {
            // Đúng câu trả lời lệnh này sẽ nhận nếu tới chậm hơn một nhịp, sau khi lệnh thắng commit.
            return ClaimResult.notCompletedYet();
        }

        List<ClaimResult.ItemReward> items = effects.itemGrants().stream()
                .map(g -> new ClaimResult.ItemReward(g.itemTemplateCode(), g.quantity()))
                .toList();
        return ClaimResult.ok(effects.expGranted(), items);
    }

    /** Định danh một lệnh lĩnh — gom lại để phần ghi không vượt trần tham số. */
    private record ClaimRequest(long characterId, long questId, String questCode, String idempotencyKey) {}

    /** Trao EXP + từng món thưởng trong transaction đang mở (đã khoá túi đồ, đã kiểm đủ chỗ). */
    private void grantRewards(Transaction t, long characterId, QuestScriptRunner.HookEffects effects) {
        rewardPorts.expPort().grantExp(t, characterId, effects.expGranted());
        for (QuestScriptRunner.ItemGrant itemGrant : effects.itemGrants()) {
            rewardPorts
                    .inventoryGrantPort()
                    .grantItemByCode(t, characterId, itemGrant.itemTemplateCode(), itemGrant.quantity());
        }
    }

    /**
     * Túi còn đủ ô cho trọn lô phần thưởng không — hỏi TRƯỚC khi ghi bất cứ thứ gì.
     *
     * <p>Túi đầy KHÔNG ném exception ({@code Inventory.add} coi đó là trạng thái bình thường,
     * adapter chỉ trả {@code false}), nên guard {@code scriptFailed} KHÔNG che được ca này. Bug đo
     * thật 2026-09-13: quest 11 khai trao 6 món, túi chỉ nhận 5, mà response vẫn OK và
     * {@code idempotency_key} vẫn bị đốt ⇒ mất món vĩnh viễn.
     *
     * <p>Hỏi TRONG transaction ghi, sau khi đã khoá túi đồ (sửa 2026-09-25). Trước đó câu hỏi chạy
     * ở một transaction CHỈ ĐỌC riêng: một lần mua hàng commit chen giữa hai transaction làm câu trả
     * lời "đủ chỗ" thành sai lúc trao — túi 21 ô, hoặc món thưởng bị bỏ rơi trong im lặng vì kết
     * quả của {@code grantItemByCode} không được kiểm. Gọi trước {@code recordOrThrow} nên
     * {@code idempotency_key} vẫn còn nguyên khi trả về {@code INVENTORY_FULL}.
     */
    private boolean hasRoomForRewards(Transaction t, long characterId, List<QuestScriptRunner.ItemGrant> itemGrants) {
        if (itemGrants.isEmpty()) {
            return true;
        }
        List<QuestInventoryGrantPort.ItemGrantRequest> requests = itemGrants.stream()
                .map(g -> new QuestInventoryGrantPort.ItemGrantRequest(g.itemTemplateCode(), g.quantity()))
                .toList();
        return rewardPorts.inventoryGrantPort().hasRoomFor(t, characterId, requests);
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
