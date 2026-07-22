package dev.kage.inventory.api;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.ItemStack;
import java.util.List;

/**
 * API công khai của {@code feature-inventory} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code inventory_item}/{@code equipment_slot}/{@code drop_table*} trực
 * tiếp (Điều 24).
 */
public final class InventoryFacade {

    private final AddItemUseCase addItemUseCase;
    private final GrantLootUseCase grantLootUseCase;
    private final EquipItemUseCase equipItemUseCase;
    private final UnequipItemUseCase unequipItemUseCase;
    private final ComputeEquipmentBonusUseCase computeEquipmentBonusUseCase;

    public InventoryFacade(
            AddItemUseCase addItemUseCase, GrantLootUseCase grantLootUseCase, EquipmentUseCases equipmentUseCases) {
        this.addItemUseCase = addItemUseCase;
        this.grantLootUseCase = grantLootUseCase;
        this.equipItemUseCase = equipmentUseCases.equipItemUseCase();
        this.unequipItemUseCase = equipmentUseCases.unequipItemUseCase();
        this.computeEquipmentBonusUseCase = equipmentUseCases.computeEquipmentBonusUseCase();
    }

    /**
     * Thêm item vào túi nhân vật.
     *
     * @return {@link Result#FULL} nếu túi đầy (không ném exception — túi đầy là bình thường)
     */
    public Result addItem(long characterId, ItemStack stack) {
        return switch (addItemUseCase.execute(characterId, stack)) {
            case OK -> Result.OK;
            case FULL -> Result.FULL;
        };
    }

    /**
     * Roll {@code dropTableCode} và cộng item rơi được vào túi {@code characterId} (GAMEPLAY.md
     * §5 "Rơi đồ" — quái chết gọi hàm này với người hạ gục).
     *
     * @return item THẬT SỰ đã cộng được (rỗng nếu không rơi gì hoặc túi đầy hết) — dùng để báo
     *     client biết nhặt được gì
     */
    public List<ItemStack> grantLoot(long characterId, String dropTableCode) {
        return grantLootUseCase.execute(characterId, dropTableCode);
    }

    /**
     * Mặc item {@code itemId} (ID thật của {@code inventory_item}, không phải vị trí) vào
     * {@code targetSlot} — server tự kiểm tra item thuộc đúng nhân vật + đúng loại slot.
     */
    public EquipResult equipItem(long characterId, long itemId, EquipmentSlotType targetSlot) {
        return switch (equipItemUseCase.execute(characterId, itemId, targetSlot)) {
            case OK -> EquipResult.OK;
            case ITEM_NOT_FOUND -> EquipResult.ITEM_NOT_FOUND;
            case WRONG_SLOT -> EquipResult.WRONG_SLOT;
        };
    }

    /** Gỡ trang bị khỏi {@code slot} — item vẫn còn trong túi đồ, không bị xoá. */
    public UnequipResult unequipItem(long characterId, EquipmentSlotType slot) {
        return switch (unequipItemUseCase.execute(characterId, slot)) {
            case OK -> UnequipResult.OK;
            case NOT_EQUIPPED -> UnequipResult.NOT_EQUIPPED;
        };
    }

    /**
     * Tổng chỉ số cộng thêm từ TOÀN BỘ trang bị đang mặc (GAMEPLAY.md §2) — gọi lại sau
     * {@link #equipItem}/{@link #unequipItem} thành công VÀ lúc attach nhân vật vào world (server-
     * gateway nối sang {@code WorldFacade.applyEquipmentBonus}, server-world không tự biết module này).
     */
    public EquipmentBonus computeEquipmentBonus(long characterId) {
        return computeEquipmentBonusUseCase.execute(characterId);
    }

    public enum Result { OK, FULL }

    public enum EquipResult { OK, ITEM_NOT_FOUND, WRONG_SLOT }

    public enum UnequipResult { OK, NOT_EQUIPPED }
}
