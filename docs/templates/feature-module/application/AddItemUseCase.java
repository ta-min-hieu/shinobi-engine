// TEMPLATE — UseCase: ĐIỀU PHỐI, không chứa quy tắc nghiệp vụ (quy tắc ở domain).
package dev.kage.inventory.application;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionManager;

/**
 * Thêm item vào túi. Điều phối: load → gọi domain → lưu trong transaction.
 *
 * <p>Constructor Injection tường minh — không DI container, không field injection (ADR-009).
 */
public final class AddItemUseCase {

    private final InventoryPort inventoryPort;
    private final TransactionManager tx;

    public AddItemUseCase(InventoryPort inventoryPort, TransactionManager tx) {
        this.inventoryPort = inventoryPort;
        this.tx = tx;
    }

    public AddItemResult execute(long characterId, ItemStack stack) {
        var inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        var result = inventory.add(stack);                 // quy tắc nghiệp vụ ở domain
        if (result instanceof Inventory.Full) {
            return AddItemResult.FULL;
        }

        tx.inTransaction((Transaction t) -> inventoryPort.save(t, inventory));
        return AddItemResult.OK;
    }

    public enum AddItemResult { OK, FULL }
}
