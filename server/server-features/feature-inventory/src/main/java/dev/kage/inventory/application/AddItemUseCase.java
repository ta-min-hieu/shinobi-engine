package dev.kage.inventory.application;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Thêm item vào túi. Điều phối: load → gọi domain → lưu trong transaction.
 *
 * <p>Constructor Injection tường minh — không DI container, không field injection (ADR-009).
 */
public final class AddItemUseCase {

    private final InventoryPort inventoryPort;
    private final ItemTemplatePort itemTemplatePort;
    private final TransactionRunner tx;

    public AddItemUseCase(InventoryPort inventoryPort, ItemTemplatePort itemTemplatePort, TransactionRunner tx) {
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.tx = tx;
    }

    public AddItemResult execute(long characterId, ItemStack stack) {
        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        // maxStack phải tra từ item_template: domain không tự biết loại item nào xếp chồng được.
        // Thiếu template (dữ liệu hỏng) ⇒ coi như KHÔNG xếp chồng — an toàn hơn là gộp nhầm.
        int maxStack = itemTemplatePort
                .findById(stack.itemTemplateId())
                .map(template -> template.maxStack())
                .orElse(1);
        Inventory.AddResult result = inventory.add(stack, maxStack); // quy tắc nghiệp vụ ở domain
        if (result instanceof Inventory.Full) {
            return AddItemResult.FULL;
        }

        tx.inTransaction((Transaction t) -> inventoryPort.save(t, inventory));
        return AddItemResult.OK;
    }

    public enum AddItemResult {
        OK,
        FULL
    }
}
