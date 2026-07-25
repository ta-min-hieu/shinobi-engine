package dev.kage.inventory.application;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Mặc trang bị (GAMEPLAY.md §7, DATABASE.md §2.2). Điều phối: đọc túi đồ (item có tồn tại, thuộc
 * đúng nhân vật không — Điều 24, không tin {@code itemId} từ client) → đọc {@code item_template}
 * (đúng slot không) → gọi domain {@link dev.kage.inventory.domain.Equipment#equip} → lưu.
 *
 * <p>Constructor Injection tường minh — không DI container, không field injection (ADR-009).
 */
public final class EquipItemUseCase {

    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final EquipmentPort equipmentPort;
    private final TransactionRunner tx;

    public EquipItemUseCase(
            InventoryPort inventoryPort, ItemTemplatePort itemTemplatePort, EquipmentPort equipmentPort,
            TransactionRunner tx) {
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.equipmentPort = equipmentPort;
        this.tx = tx;
    }

    public EquipItemResult execute(long characterId, long itemId, EquipmentSlotType targetSlot) {
        Inventory inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        ItemStack item = inventory.findItem(itemId).orElse(null);
        if (item == null) {
            // item không tồn tại HOẶC không thuộc characterId này — cùng lỗi (fail secure, SECURITY.md §4)
            return EquipItemResult.ITEM_NOT_FOUND;
        }

        ItemTemplate template = itemTemplatePort.findById(item.itemTemplateId())
                .orElseThrow(() -> new IllegalStateException(
                        "item_template không tồn tại cho itemTemplateId=" + item.itemTemplateId()));
        if (template.equipSlot() != targetSlot) {
            return EquipItemResult.WRONG_SLOT;
        }

        Equipment equipment = equipmentPort.findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        equipment.equip(targetSlot, itemId);
        tx.inTransaction((Transaction t) -> equipmentPort.save(t, equipment));
        return EquipItemResult.OK;
    }

    public enum EquipItemResult { OK, ITEM_NOT_FOUND, WRONG_SLOT }
}
