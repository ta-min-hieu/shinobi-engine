package dev.kage.inventory.application;

import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemTemplatePort;

/**
 * Tính TỔNG chỉ số cộng thêm từ mọi item đang mặc (GAMEPLAY.md §2/§7) — đọc thuần, không ghi.
 * Gọi lại MỖI lần trang bị đổi VÀ ngay lúc attach nhân vật vào world (server-world không tự biết
 * feature-inventory — {@code GatewayChannelHandler} là nơi nối 2 bên, xem
 * {@code dev.kage.server.world.WorldFacade#applyEquipmentBonus}).
 */
public final class ComputeEquipmentBonusUseCase {

    private final EquipmentPort equipmentPort;
    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;

    public ComputeEquipmentBonusUseCase(
            EquipmentPort equipmentPort, InventoryPort inventoryPort, ItemTemplatePort itemTemplatePort) {
        this.equipmentPort = equipmentPort;
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
    }

    public EquipmentBonus execute(long characterId) {
        var equipment = equipmentPort.findByCharacter(characterId)
                .orElseThrow(() -> new IllegalStateException("EquipmentPort phải luôn trả Optional có giá trị"));
        var inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        int totalBonusAtk = 0;
        for (long itemId : equipment.slots().values()) {
            var item = inventory.findItem(itemId).orElse(null);
            if (item == null) {
                continue; // dữ liệu lệch hiếm gặp (item bị xoá khỏi túi nhưng equipment_slot chưa dọn) — bỏ qua an toàn
            }
            var template = itemTemplatePort.findById(item.itemTemplateId()).orElse(null);
            if (template == null) {
                continue;
            }
            totalBonusAtk += template.bonusAtk();
        }
        return new EquipmentBonus(totalBonusAtk);
    }
}
