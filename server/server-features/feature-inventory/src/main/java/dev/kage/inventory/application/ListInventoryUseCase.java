package dev.kage.inventory.application;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.InventorySnapshot;

/**
 * Ảnh chụp TOÀN BỘ túi đồ + trang bị đang mặc (ADR-035 Decision-1) — đọc thuần, không ghi, không
 * cần transaction (cùng lý do {@link ComputeEquipmentBonusUseCase}).
 *
 * <p>Không phân trang: sức chứa túi là hằng số nhỏ phía code ({@code PostgresInventoryAdapter}), và
 * phân trang là độ phức tạp trả cho một vấn đề chưa tồn tại (Điều 9, ADR-035 Revisit Trigger #2).
 */
public final class ListInventoryUseCase {

    private final InventoryPort inventoryPort;
    private final EquipmentPort equipmentPort;

    public ListInventoryUseCase(InventoryPort inventoryPort, EquipmentPort equipmentPort) {
        this.inventoryPort = inventoryPort;
        this.equipmentPort = equipmentPort;
    }

    public InventorySnapshot execute(long characterId) {
        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));
        Equipment equipment = equipmentPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        return new InventorySnapshot(inventory, equipment);
    }
}
