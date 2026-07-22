package dev.kage.inventory.application;

import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.LootRoller;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Quái chết → roll {@code drop_table} → cộng item rơi được vào túi người hạ gục (GAMEPLAY.md §5).
 * Điều phối: roll (domain, thuần) → load túi → cộng từng item → lưu 1 lần.
 *
 * <p>⚠️ Item rơi mà túi đầy giữa chừng thì BỊ MẤT (không rơi xuống đất) — feature-inventory/
 * CLAUDE.md ghi rõ đây là hạn chế tạm thời của lát cắt này, không phải hành vi cuối cùng.
 */
public final class GrantLootUseCase {

    private final DropTablePort dropTablePort;
    private final InventoryPort inventoryPort;
    private final TransactionRunner tx;

    public GrantLootUseCase(DropTablePort dropTablePort, InventoryPort inventoryPort, TransactionRunner tx) {
        this.dropTablePort = dropTablePort;
        this.inventoryPort = inventoryPort;
        this.tx = tx;
    }

    /**
     * @return item THẬT SỰ đã cộng được, MANG {@code ItemStack.id} THẬT (đọc từ {@code Inventory}
     *     mà {@link InventoryPort#save} trả về, KHÔNG phải object truyền vào {@code add()} — cái
     *     đó vẫn còn {@code id == NEW_ITEM_ID}) — client cần ID thật này để sau này mặc đồ
     *     (`EquipItemCommand`).
     */
    public List<ItemStack> execute(long characterId, String dropTableCode) {
        var entries = dropTablePort.findEntries(dropTableCode);
        var drops = LootRoller.roll(entries);
        if (drops.isEmpty()) {
            return List.of();
        }

        var inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        // Inventory.add() luôn APPEND (xem javadoc) — vị trí cuối cùng ngay sau add() chính là vị
        // trí của item đó trong Inventory đã lưu trả về (save() giữ nguyên thứ tự), nên nhớ INDEX
        // là đủ để tra lại đúng item, không cần so khớp itemTemplateId/quantity (có thể trùng nhau
        // giữa 2 lần rơi khác nhau, dễ tra nhầm).
        var addedIndexes = new ArrayList<Integer>();
        for (var stack : drops) {
            if (inventory.add(stack) instanceof Inventory.Added) {
                addedIndexes.add(inventory.items().size() - 1);
            }
        }
        if (addedIndexes.isEmpty()) {
            return List.of();
        }

        var savedHolder = new AtomicReference<Inventory>();
        tx.inTransaction((Transaction t) -> savedHolder.set(inventoryPort.save(t, inventory)));
        var savedItems = savedHolder.get().items();

        var granted = new ArrayList<ItemStack>(addedIndexes.size());
        for (int index : addedIndexes) {
            granted.add(savedItems.get(index));
        }
        return List.copyOf(granted);
    }
}
