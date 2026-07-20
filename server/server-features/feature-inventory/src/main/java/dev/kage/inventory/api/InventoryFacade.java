package dev.kage.inventory.api;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.application.GrantLootUseCase;
import dev.kage.inventory.domain.ItemStack;
import java.util.List;

/**
 * API công khai của {@code feature-inventory} — module khác (server-gateway) CHỈ gọi qua đây,
 * không bao giờ đọc bảng {@code inventory_item}/{@code drop_table*} trực tiếp (Điều 24).
 */
public final class InventoryFacade {

    private final AddItemUseCase addItemUseCase;
    private final GrantLootUseCase grantLootUseCase;

    public InventoryFacade(AddItemUseCase addItemUseCase, GrantLootUseCase grantLootUseCase) {
        this.addItemUseCase = addItemUseCase;
        this.grantLootUseCase = grantLootUseCase;
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

    public enum Result { OK, FULL }
}
