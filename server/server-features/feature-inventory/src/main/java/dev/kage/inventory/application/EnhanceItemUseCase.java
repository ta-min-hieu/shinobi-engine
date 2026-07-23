package dev.kage.inventory.application;

import dev.kage.inventory.domain.AuditPort;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.GoldPort;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Cường hoá item +0→+15 (GAMEPLAY.md §7) — tốn gold, tỉ lệ thành công giảm dần theo cấp
 * ({@link EnhancementTable}), TRỪ GOLD + ĐỔI CẤP + GHI AUDIT trong 1 transaction (DATABASE.md §5:
 * trừ gold thành công nhưng ghi cấp thất bại = mất tài sản thật).
 *
 * <p>⚠️ Chưa có "Bảo Vệ" (item/flag chống rớt cấp) — từ
 * {@link EnhancementTable#DROP_ON_FAIL_FROM_LEVEL} trở lên, thất bại LUÔN rớt 1 cấp
 * ({@link EnhanceResult#FAILED_DROPPED}). Quyết định của chủ dự án cho lát cắt này (chừa chỗ bằng
 * 2 nhánh fail riêng, code Bảo Vệ ở lát cắt sau).
 */
public final class EnhanceItemUseCase {

    private final InventoryPort inventoryPort;
    private final GoldPort goldPort;
    private final AuditPort auditPort;
    private final TransactionRunner tx;

    public EnhanceItemUseCase(
            InventoryPort inventoryPort, GoldPort goldPort, AuditPort auditPort, TransactionRunner tx) {
        this.inventoryPort = inventoryPort;
        this.goldPort = goldPort;
        this.auditPort = auditPort;
        this.tx = tx;
    }

    public EnhanceResult execute(long characterId, long itemId) {
        var inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        var item = inventory.findItem(itemId).orElse(null);
        if (item == null) {
            return EnhanceResult.ITEM_NOT_FOUND;
        }
        int currentLevel = item.enhanceLevel();
        if (currentLevel >= ItemStack.MAX_ENHANCE) {
            return EnhanceResult.MAX_LEVEL;
        }

        long cost = EnhancementTable.goldCost(currentLevel);
        int successRate = EnhancementTable.successRatePercent(currentLevel);
        boolean rolledSuccess = ThreadLocalRandom.current().nextInt(100) < successRate;

        var resultHolder = new AtomicReference<EnhanceResult>();
        tx.inTransaction((Transaction t) -> {
            if (!goldPort.deductGold(t, characterId, cost)) {
                resultHolder.set(EnhanceResult.INSUFFICIENT_GOLD);
                return;
            }
            var outcome = resolveOutcome(currentLevel, rolledSuccess);
            inventory.replaceItem(new ItemStack(item.id(), item.itemTemplateId(), item.quantity(), outcome.toLevel()));
            inventoryPort.save(t, inventory);
            auditPort.log(t, characterId, "ENHANCE_ITEM", detailJson(itemId, cost, outcome));
            resultHolder.set(outcome.result());
        });
        return resultHolder.get();
    }

    private static EnhanceOutcome resolveOutcome(int currentLevel, boolean rolledSuccess) {
        if (rolledSuccess) {
            return new EnhanceOutcome(currentLevel, currentLevel + 1, EnhanceResult.SUCCESS);
        }
        if (currentLevel >= EnhancementTable.DROP_ON_FAIL_FROM_LEVEL) {
            return new EnhanceOutcome(currentLevel, currentLevel - 1, EnhanceResult.FAILED_DROPPED);
        }
        return new EnhanceOutcome(currentLevel, currentLevel, EnhanceResult.FAILED_NO_DROP);
    }

    private static String detailJson(long itemId, long cost, EnhanceOutcome outcome) {
        return "{\"itemId\":%d,\"fromLevel\":%d,\"toLevel\":%d,\"goldCost\":%d,\"result\":\"%s\"}"
                .formatted(itemId, outcome.fromLevel(), outcome.toLevel(), cost, outcome.result());
    }

    private record EnhanceOutcome(int fromLevel, int toLevel, EnhanceResult result) {
    }

    public enum EnhanceResult { SUCCESS, FAILED_NO_DROP, FAILED_DROPPED, ITEM_NOT_FOUND, MAX_LEVEL, INSUFFICIENT_GOLD }
}
