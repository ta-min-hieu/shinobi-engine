package dev.kage.inventory.domain;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/**
 * Trang bị đang mặc của 1 nhân vật (DATABASE.md §2.2 {@code equipment_slot}) — aggregate RIÊNG
 * với {@link Inventory}, chỉ giữ tham chiếu {@code ItemStack.id} đang mặc ở mỗi slot, không giữ
 * bản sao dữ liệu item (item thật vẫn nằm trong {@link Inventory}, đọc/ghi qua
 * {@code inventory_item.id} thật — Phase 5 lát cắt đầu). Slot vắng mặt trong map = KHÔNG mặc gì.
 *
 * <p>Domain thuần: không import infrastructure, test được không cần mock (Điều 18).
 */
public final class Equipment {

    private final long characterId;
    private final Map<EquipmentSlotType, Long> equippedItemIds;

    public Equipment(long characterId, Map<EquipmentSlotType, Long> equippedItemIds) {
        this.characterId = characterId;
        // KHÔNG dùng new EnumMap<>(equippedItemIds) trực tiếp — ném IllegalArgumentException nếu
        // map nguồn RỖNG và không phải EnumMap (không đủ để EnumMap suy luận kiểu enum, xem javadoc
        // EnumMap(Map)). Tạo EnumMap rỗng tường minh theo Class rồi putAll.
        this.equippedItemIds = new EnumMap<>(EquipmentSlotType.class);
        this.equippedItemIds.putAll(equippedItemIds);
    }

    public long characterId() {
        return characterId;
    }

    public Optional<Long> equippedItemId(EquipmentSlotType slot) {
        return Optional.ofNullable(equippedItemIds.get(slot));
    }

    public Map<EquipmentSlotType, Long> slots() {
        return Map.copyOf(equippedItemIds);
    }

    /**
     * Mặc {@code itemId} vào {@code slot} — GHI ĐÈ item cũ đang mặc ở slot đó (nếu có), coi như tự
     * gỡ ra. Không tự kiểm tra {@code itemId} có thuộc đúng loại slot hay không — đó là việc của
     * {@code EquipItemUseCase} (cần {@link ItemTemplatePort}, aggregate này không biết item_template).
     */
    public void equip(EquipmentSlotType slot, long itemId) {
        equippedItemIds.put(slot, itemId);
    }

    /** Quy tắc nghiệp vụ "gỡ đồ" ở domain, không phải UseCase — trả kết quả thay vì boolean trần. */
    public UnequipResult unequip(EquipmentSlotType slot) {
        if (equippedItemIds.remove(slot) == null) {
            return UnequipResult.NOT_EQUIPPED;
        }
        return UnequipResult.UNEQUIPPED;
    }

    public enum UnequipResult {
        UNEQUIPPED,
        NOT_EQUIPPED
    }
}
