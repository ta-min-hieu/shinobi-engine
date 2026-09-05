package dev.kage.inventory.application;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.LootRoller;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

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
    private final ItemTemplatePort itemTemplatePort;
    private final TransactionRunner tx;

    public GrantLootUseCase(
            DropTablePort dropTablePort,
            InventoryPort inventoryPort,
            ItemTemplatePort itemTemplatePort,
            TransactionRunner tx) {
        this.dropTablePort = dropTablePort;
        this.inventoryPort = inventoryPort;
        this.itemTemplatePort = itemTemplatePort;
        this.tx = tx;
    }

    /**
     * Roll bảng rơi rồi cộng vào túi người hạ gục.
     *
     * @return BIÊN LAI của những gì vừa cộng được: {@code id} là {@code inventory_item.id} THẬT
     *     (đọc từ {@code Inventory} mà {@link InventoryPort#save} trả về — object truyền vào
     *     {@code add()} vẫn còn {@code id == NEW_ITEM_ID}), client cần nó để mặc đồ
     *     ({@code EquipItemCommand}); còn {@code quantity} là SỐ VỪA NHẬN, <b>không phải</b> tổng
     *     của chồng.
     *     <p>⚠️ Phân biệt này chỉ thành ra quan trọng từ khi có gộp chồng (2026-09-05): nhặt 2 phi
     *     đao vào chồng đang có 15 thì {@code ItemGrantedEvent} phải báo "nhận được 2", không phải
     *     "nhận được 17". Trước đó mỗi lần rơi luôn tạo ô mới nên hai con số tình cờ bằng nhau.
     */
    public List<ItemStack> execute(long characterId, String dropTableCode) {
        List<DropTableEntry> entries = dropTablePort.findEntries(dropTableCode);
        List<ItemStack> drops = LootRoller.roll(entries);
        if (drops.isEmpty()) {
            return List.of();
        }

        Inventory inventory = inventoryPort
                .findByCharacter(characterId)
                .orElseThrow(() -> new CharacterInventoryNotFoundException(characterId));

        // Vị trí do chính Inventory.Added báo về — KHÔNG đoán bằng items().size() - 1 nữa: từ khi
        // có gộp chồng, item vừa cộng có thể nằm ở một ô CŨ chứ không phải ô cuối. save() giữ
        // nguyên thứ tự nên chỉ số này tra lại đúng item trong Inventory đã lưu.
        ArrayList<int[]> placements = new ArrayList<int[]>(); // {index, soLuongVuaNhan}
        for (ItemStack stack : drops) {
            if (inventory.add(stack, maxStackOf(stack)) instanceof Inventory.Added added) {
                placements.add(new int[] {added.index(), stack.quantity()});
            }
        }
        if (placements.isEmpty()) {
            return List.of();
        }

        AtomicReference<Inventory> savedHolder = new AtomicReference<Inventory>();
        tx.inTransaction((Transaction t) -> savedHolder.set(inventoryPort.save(t, inventory)));
        List<ItemStack> savedItems = savedHolder.get().items();

        ArrayList<ItemStack> granted = new ArrayList<ItemStack>(placements.size());
        for (int[] placement : placements) {
            ItemStack saved = savedItems.get(placement[0]);
            // quantity = SỐ VỪA NHẬN (xem javadoc), id/enhanceLevel = của chồng thật trong DB.
            granted.add(new ItemStack(saved.id(), saved.itemTemplateId(), placement[1], saved.enhanceLevel()));
        }
        return List.copyOf(granted);
    }

    /** Thiếu template (dữ liệu hỏng) ⇒ coi như KHÔNG xếp chồng — an toàn hơn là gộp nhầm. */
    private int maxStackOf(ItemStack stack) {
        return itemTemplatePort
                .findById(stack.itemTemplateId())
                .map(template -> template.maxStack())
                .orElse(1);
    }
}
