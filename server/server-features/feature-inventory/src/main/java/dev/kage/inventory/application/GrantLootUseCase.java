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

    public List<ItemStack> execute(long characterId, String dropTableCode) {
        var entries = dropTablePort.findEntries(dropTableCode);
        var drops = LootRoller.roll(entries);
        if (drops.isEmpty()) {
            return List.of();
        }

        var inventory = inventoryPort.findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        var granted = new ArrayList<ItemStack>();
        for (var stack : drops) {
            if (inventory.add(stack) instanceof Inventory.Added) {
                granted.add(stack);
            }
        }
        if (!granted.isEmpty()) {
            tx.inTransaction((Transaction t) -> inventoryPort.save(t, inventory));
        }
        return List.copyOf(granted);
    }
}
