package dev.kage.inventory.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/** Gỡ trang bị khỏi 1 slot (GAMEPLAY.md §7). Item KHÔNG bị xoá — vẫn nằm trong túi đồ như trước. */
public final class UnequipItemUseCase {

    private final EquipmentPort equipmentPort;
    private final TransactionRunner tx;
    private final Optional<InventoryPort> bagPort;

    /** Bản KHÔNG kiểm chỗ trong túi — cho test chỉ quan tâm tới ô trang bị. Production dùng {@link #guardedBy}. */
    public UnequipItemUseCase(EquipmentPort equipmentPort, TransactionRunner tx) {
        this(equipmentPort, tx, Optional.empty());
    }

    private UnequipItemUseCase(EquipmentPort equipmentPort, TransactionRunner tx, Optional<InventoryPort> bagPort) {
        this.equipmentPort = equipmentPort;
        this.tx = tx;
        this.bagPort = bagPort;
    }

    /**
     * Bản từ chối gỡ đồ khi túi đã đầy (quyết định chủ dự án 2026-10-11). Từ khi đồ đang mặc không chiếm
     * ô túi, gỡ một món là đưa nó VÀO túi; không kiểm thì gỡ-rồi-mặc-món-khác là cách nhét quá 20 món.
     */
    public UnequipItemUseCase guardedBy(InventoryPort inventoryPort) {
        return new UnequipItemUseCase(equipmentPort, tx, Optional.of(inventoryPort));
    }

    public UnequipItemResult execute(long characterId, EquipmentSlotType slot) {
        Equipment equipment = equipmentPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));

        if (equipment.unequip(slot) == Equipment.UnequipResult.NOT_EQUIPPED) {
            return UnequipItemResult.NOT_EQUIPPED;
        }
        AtomicReference<UnequipItemResult> result = new AtomicReference<>(UnequipItemResult.OK);
        tx.inTransaction((Transaction t) -> {
            if (isBagFull(t, characterId)) {
                result.set(UnequipItemResult.INVENTORY_FULL);
                return;
            }
            equipmentPort.save(t, equipment);
        });
        return result.get();
    }

    /**
     * Sức chứa {@code Inventory} nhận về là "ô túi + số món đang mặc" và nó chứa cả món đang mặc, nên
     * "đã chạm sức chứa" đúng bằng "số món nằm trong túi đã đủ". Đọc sau khoá túi đồ, trong transaction.
     */
    private boolean isBagFull(Transaction t, long characterId) {
        return bagPort.flatMap(port -> port.findByCharacterForUpdate(t, characterId))
                .map(inventory -> inventory.items().size() >= inventory.capacity())
                .orElse(false);
    }

    public enum UnequipItemResult {
        OK,
        NOT_EQUIPPED,
        /** Túi đã đầy — món đang mặc không có chỗ để về (2026-10-11). Không đổi gì. */
        INVENTORY_FULL
    }
}
