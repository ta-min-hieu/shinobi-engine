package dev.kage.inventory.api;

import java.util.List;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.ComputeEquipmentBonusUseCase;
import dev.kage.inventory.application.EnhanceItemUseCase;
import dev.kage.inventory.application.EquipItemUseCase;
import dev.kage.inventory.application.GetItemCatalogUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.application.ListInventoryUseCase;
import dev.kage.inventory.application.UnequipItemUseCase;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemCatalog;
import dev.kage.inventory.domain.ItemStack;

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
    private final EnhanceItemUseCase enhanceItemUseCase;
    private final ListInventoryUseCase listInventoryUseCase;
    private final GetItemCatalogUseCase getItemCatalogUseCase;

    public InventoryFacade(
            ItemAcquisitionUseCases acquisitionUseCases,
            EquipmentUseCases equipmentUseCases,
            EnhanceItemUseCase enhanceItemUseCase,
            InventoryQueryUseCases queryUseCases) {
        this.addItemUseCase = acquisitionUseCases.addItemUseCase();
        this.grantLootUseCase = acquisitionUseCases.grantLootUseCase();
        this.equipItemUseCase = equipmentUseCases.equipItemUseCase();
        this.unequipItemUseCase = equipmentUseCases.unequipItemUseCase();
        this.computeEquipmentBonusUseCase = equipmentUseCases.computeEquipmentBonusUseCase();
        this.enhanceItemUseCase = enhanceItemUseCase;
        this.listInventoryUseCase = queryUseCases.listInventoryUseCase();
        this.getItemCatalogUseCase = queryUseCases.getItemCatalogUseCase();
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

    /**
     * Cường hoá item {@code itemId} (ID thật, không phải vị trí) — tốn gold, tỉ lệ thành công giảm
     * dần theo cấp (GAMEPLAY.md §7). Từ +10 trở lên, thất bại rớt 1 cấp ({@code FAILED_DROPPED})
     * TRỪ KHI {@code useProtection=true} VÀ đủ "Bảo Vệ" trong túi ({@code FAILED_PROTECTED}, §7.1).
     * {@code idempotencyKey} (ADR-025) — client sinh UUID, giữ nguyên khi tự retry.
     */
    public EnhanceResult enhanceItem(long characterId, long itemId, boolean useProtection, String idempotencyKey) {
        return switch (enhanceItemUseCase.execute(characterId, itemId, useProtection, idempotencyKey)) {
            case SUCCESS -> EnhanceResult.SUCCESS;
            case FAILED_NO_DROP -> EnhanceResult.FAILED_NO_DROP;
            case FAILED_DROPPED -> EnhanceResult.FAILED_DROPPED;
            case FAILED_PROTECTED -> EnhanceResult.FAILED_PROTECTED;
            case ITEM_NOT_FOUND -> EnhanceResult.ITEM_NOT_FOUND;
            case MAX_LEVEL -> EnhanceResult.MAX_LEVEL;
            case INSUFFICIENT_GOLD -> EnhanceResult.INSUFFICIENT_GOLD;
            case DUPLICATE_REQUEST -> EnhanceResult.DUPLICATE_REQUEST;
        };
    }

    /**
     * Ảnh chụp TOÀN BỘ túi đồ + trang bị đang mặc của {@code characterId} (ADR-035 Decision-1) —
     * nguồn DUY NHẤT để client biết {@code inventory_item.id} thật sau khi đăng nhập lại, thứ mà
     * {@code EquipItemCommand}/{@code SellItemCommand} bắt buộc phải có.
     */
    public InventorySnapshot listInventory(long characterId) {
        return listInventoryUseCase.execute(characterId);
    }

    /**
     * Toàn bộ catalog vật phẩm + {@code version} là hash nội dung do server tính (ADR-035
     * Decision-2, phương án A′) — gateway so {@code version} này với bản client gửi lên để quyết
     * gửi lại hay báo {@code unchanged}. Có cache trong bộ nhớ, xem
     * {@link GetItemCatalogUseCase} cho điều kiện làm mất hiệu lực.
     */
    public ItemCatalog itemCatalog() {
        return getItemCatalogUseCase.execute();
    }

    public enum Result {
        OK,
        FULL
    }

    public enum EquipResult {
        OK,
        ITEM_NOT_FOUND,
        WRONG_SLOT
    }

    public enum UnequipResult {
        OK,
        NOT_EQUIPPED
    }

    public enum EnhanceResult {
        SUCCESS,
        FAILED_NO_DROP,
        FAILED_DROPPED,
        FAILED_PROTECTED,
        ITEM_NOT_FOUND,
        MAX_LEVEL,
        INSUFFICIENT_GOLD,
        DUPLICATE_REQUEST
    }
}
