// TEMPLATE — Facade: public API DUY NHẤT của module. Module khác CHỈ gọi qua đây.
package dev.kage.inventory.api;

import dev.kage.inventory.application.AddItemUseCase;
import dev.kage.inventory.domain.ItemStack;

/**
 * API công khai của Inventory. Module khác (quest, shop...) chỉ được gọi qua Facade,
 * không với tay vào application/domain/infrastructure ([ARCHITECTURE §9]).
 *
 * <p>Public API → bắt buộc JavaDoc (Điều 16 CODING-STANDARDS).
 */
public final class InventoryFacade {

    private final AddItemUseCase addItem;

    public InventoryFacade(AddItemUseCase addItem) {
        this.addItem = addItem;
    }

    /**
     * Thêm item vào túi nhân vật.
     *
     * @param characterId nhân vật nhận item
     * @param stack       item + số lượng
     * @return {@link Result#FULL} nếu túi đầy (không ném exception — túi đầy là bình thường)
     */
    public Result addItem(long characterId, ItemStack stack) {
        return switch (addItem.execute(characterId, stack)) {
            case OK   -> Result.OK;
            case FULL -> Result.FULL;
        };
    }

    public enum Result { OK, FULL }
}
