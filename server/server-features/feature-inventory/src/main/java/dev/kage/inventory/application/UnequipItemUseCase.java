package dev.kage.inventory.application;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/** Gỡ trang bị khỏi 1 slot (GAMEPLAY.md §7). Item KHÔNG bị xoá — vẫn nằm trong túi đồ như trước. */
public final class UnequipItemUseCase {

    private final EquipmentPort equipmentPort;
    private final TransactionRunner tx;

    public UnequipItemUseCase(EquipmentPort equipmentPort, TransactionRunner tx) {
        this.equipmentPort = equipmentPort;
        this.tx = tx;
    }

    public UnequipItemResult execute(long characterId, EquipmentSlotType slot) {
        var equipment = equipmentPort.findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));

        if (equipment.unequip(slot) == Equipment.UnequipResult.NOT_EQUIPPED) {
            return UnequipItemResult.NOT_EQUIPPED;
        }
        tx.inTransaction((Transaction t) -> equipmentPort.save(t, equipment));
        return UnequipItemResult.OK;
    }

    public enum UnequipItemResult { OK, NOT_EQUIPPED }
}
