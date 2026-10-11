package dev.kage.inventory.application;

import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.IdempotencyKeyReusedException;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Cường hoá item +0→+15 (GAMEPLAY.md §7) — tốn gold, tỉ lệ thành công giảm dần theo cấp
 * ({@link EnhancementTable}), ghi {@code idempotencyKey} (ADR-025 — chống double-effect khi client
 * tự retry) + TRỪ GOLD + ĐỔI CẤP + GHI AUDIT trong 1 transaction (DATABASE.md §5: trừ gold thành
 * công nhưng ghi cấp thất bại = mất tài sản thật).
 *
 * <p>"Bảo Vệ" (GAMEPLAY.md §7.1): từ {@link EnhancementTable#DROP_ON_FAIL_FROM_LEVEL} trở lên,
 * nếu {@code useProtection=true} VÀ người chơi có item {@link EnhancementTable#PROTECTION_CHARM_CODE}
 * trong túi, thất bại KHÔNG rớt cấp mà tiêu 1 Bảo Vệ ({@link EnhanceResult#FAILED_PROTECTED}).
 * Chỉ tiêu khi THẤT BẠI THẬT (roll xong mới biết) — thành công hay thất bại dưới +10 không đụng
 * tới Bảo Vệ dù client có bật cờ hay không.
 */
public final class EnhanceItemUseCase {

    private final EnhancePorts ports;
    private final TransactionRunner tx;

    public EnhanceItemUseCase(EnhancePorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    public EnhanceResult execute(long characterId, long itemId, boolean useProtection, String idempotencyKey) {
        AtomicReference<EnhanceResult> resultHolder = new AtomicReference<EnhanceResult>();
        try {
            // Đọc túi TRONG transaction, sau khoá túi đồ, TRƯỚC khi đụng vàng (sửa 2026-09-25): thứ
            // tự khoá chung của cả server (DATABASE.md §5.1). Đọc ngoài transaction thì một
            // lần mua hàng commit chen giữa bị save() coi là "đã gỡ" và xoá thật.
            tx.inTransaction((Transaction t) -> {
                Inventory inventory = ports.inventoryPort()
                        .findByCharacterForUpdate(t, characterId)
                        .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
                Optional<ItemStack> item = inventory.findItem(itemId);
                if (item.isEmpty()) {
                    resultHolder.set(EnhanceResult.ITEM_NOT_FOUND);
                    return;
                }
                // Quyết định chủ dự án 2026-10-11: chỉ TRANG BỊ mới cường hoá được. Trước đó cường hoá
                // một dòng vật phẩm xếp chồng (đan dược, nguyên liệu) là cả chồng lên cấp, và đồ đã
                // cường hoá không bao giờ gộp chồng lại được nữa. Từ chối TRƯỚC idempotency_key và vàng.
                if (!isEquipment(item.get())) {
                    resultHolder.set(EnhanceResult.NOT_EQUIPMENT);
                    return;
                }
                // Quyết định chủ dự án 2026-09-29: món đang chào trong giao dịch còn mở KHÔNG được
                // cường hoá — trade_offer_item không lưu cấp, người kia đã xác nhận trên cấp cũ. Hỏi
                // SAU khoá túi đồ: đặt lời chào cũng lấy khoá này nên hai bên xếp hàng (DATABASE.md
                // §5.1). Từ chối TRƯỚC idempotency_key: khoá chưa ghi, huỷ giao dịch xong thử lại
                // bằng chính khoá đó vẫn là cùng một lần cường hoá.
                if (ports.tradeOfferPort().isOfferedInActiveTrade(t, characterId, itemId)) {
                    resultHolder.set(EnhanceResult.ITEM_IN_TRADE);
                    return;
                }
                if (item.get().enhanceLevel() >= ItemStack.MAX_ENHANCE) {
                    resultHolder.set(EnhanceResult.MAX_LEVEL);
                    return;
                }
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                resultHolder.set(enhance(t, inventory, item.get(), useProtection));
            });
        } catch (IdempotencyKeyReusedException e) {
            return EnhanceResult.DUPLICATE_REQUEST;
        }
        return resultHolder.get();
    }

    /** Món không còn template (dữ liệu hỏng) cũng coi là không phải trang bị — không có gì để cường hoá. */
    private boolean isEquipment(ItemStack item) {
        return ports.itemTemplatePort()
                .findById(item.itemTemplateId())
                .map(template -> template.equipSlot() != null)
                .orElse(false);
    }

    /** Trừ vàng, roll, đổi cấp (+ tiêu Bảo Vệ nếu có), lưu, ghi audit — trong transaction đang mở. */
    private EnhanceResult enhance(Transaction t, Inventory inventory, ItemStack item, boolean useProtection) {
        long characterId = inventory.characterId();
        int currentLevel = item.enhanceLevel();
        long cost = EnhancementTable.goldCost(currentLevel);
        if (!ports.goldPort().deductGold(t, characterId, cost)) {
            return EnhanceResult.INSUFFICIENT_GOLD;
        }
        int successRate = EnhancementTable.successRatePercent(currentLevel);
        boolean rolledSuccess = ThreadLocalRandom.current().nextInt(100) < successRate;
        Optional<ItemStack> protectionCharge =
                findProtectionCharge(inventory, currentLevel, rolledSuccess, useProtection);
        EnhanceOutcome outcome = resolveOutcome(currentLevel, rolledSuccess, protectionCharge.isPresent());
        inventory.replaceItem(new ItemStack(item.id(), item.itemTemplateId(), item.quantity(), outcome.toLevel()));
        protectionCharge.ifPresent(charge -> inventory.consumeOne(charge.id()));
        ports.inventoryPort().save(t, inventory);
        ports.auditPort().log(t, characterId, "ENHANCE_ITEM", detailJson(item.id(), cost, outcome));
        return outcome.result();
    }

    /** {@code Optional.empty()} nếu không cần/không dùng/không có Bảo Vệ — không tiêu gì cả. */
    private Optional<ItemStack> findProtectionCharge(
            Inventory inventory, int currentLevel, boolean rolledSuccess, boolean useProtection) {
        if (!useProtection || rolledSuccess || currentLevel < EnhancementTable.DROP_ON_FAIL_FROM_LEVEL) {
            return Optional.empty();
        }
        return ports.itemTemplatePort()
                .findIdByCode(EnhancementTable.PROTECTION_CHARM_CODE)
                .flatMap(inventory::findByTemplate);
    }

    private static EnhanceOutcome resolveOutcome(int currentLevel, boolean rolledSuccess, boolean protectionUsed) {
        if (rolledSuccess) {
            return new EnhanceOutcome(currentLevel, currentLevel + 1, EnhanceResult.SUCCESS);
        }
        if (currentLevel < EnhancementTable.DROP_ON_FAIL_FROM_LEVEL) {
            return new EnhanceOutcome(currentLevel, currentLevel, EnhanceResult.FAILED_NO_DROP);
        }
        if (protectionUsed) {
            return new EnhanceOutcome(currentLevel, currentLevel, EnhanceResult.FAILED_PROTECTED);
        }
        return new EnhanceOutcome(currentLevel, currentLevel - 1, EnhanceResult.FAILED_DROPPED);
    }

    private static String detailJson(long itemId, long cost, EnhanceOutcome outcome) {
        return "{\"itemId\":%d,\"fromLevel\":%d,\"toLevel\":%d,\"goldCost\":%d,\"result\":\"%s\"}"
                .formatted(itemId, outcome.fromLevel(), outcome.toLevel(), cost, outcome.result());
    }

    private record EnhanceOutcome(int fromLevel, int toLevel, EnhanceResult result) {}

    public enum EnhanceResult {
        SUCCESS,
        FAILED_NO_DROP,
        FAILED_DROPPED,
        FAILED_PROTECTED,
        ITEM_NOT_FOUND,
        MAX_LEVEL,
        INSUFFICIENT_GOLD,
        DUPLICATE_REQUEST,
        /** Món đang nằm trong lời chào của một phiên giao dịch còn mở (2026-09-29) — không đổi gì. */
        ITEM_IN_TRADE,
        /** Món không phải trang bị — vật phẩm tiêu hao/nguyên liệu không cường hoá được (2026-10-11). */
        NOT_EQUIPMENT
    }
}
