package dev.kage.inventory.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

/**
 * Thêm item vào túi. Điều phối: khoá + load → gọi domain → lưu, tất cả trong MỘT transaction.
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
        AtomicReference<AddItemResult> result = new AtomicReference<AddItemResult>();
        // Đọc + sửa + lưu trong CÙNG một transaction, đọc sau khoá túi đồ (sửa 2026-09-25): đọc ngoài
        // transaction thì một lần mua hàng commit chen giữa bị save() coi là "đã gỡ" và xoá thật.
        tx.inTransaction((Transaction t) -> {
            Inventory inventory = inventoryPort
                    .findByCharacterForUpdate(t, characterId)
                    .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

            // maxStack phải tra từ item_template: domain không tự biết loại item nào xếp chồng được.
            // Thiếu template (dữ liệu hỏng) ⇒ coi như KHÔNG xếp chồng — an toàn hơn là gộp nhầm.
            int maxStack = itemTemplatePort
                    .findById(stack.itemTemplateId())
                    .map(template -> template.maxStack())
                    .orElse(1);
            if (inventory.add(stack, maxStack) instanceof Inventory.Full) { // quy tắc nghiệp vụ ở domain
                result.set(AddItemResult.FULL);
                return;
            }
            inventoryPort.save(t, inventory);
            result.set(AddItemResult.OK);
        });
        return result.get();
    }

    public enum AddItemResult {
        OK,
        FULL
    }
}
