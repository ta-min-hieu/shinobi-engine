package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

class GrantLootUseCaseTest {

    static final class FakeDropTablePort implements DropTablePort {
        private final List<DropTableEntry> entries;

        FakeDropTablePort(List<DropTableEntry> entries) {
            this.entries = entries;
        }

        @Override
        public List<DropTableEntry> findEntries(String dropTableCode) {
            return entries;
        }
    }

    static final class FakeInventoryPort implements InventoryPort {
        Inventory saved;
        private final Inventory existing;

        FakeInventoryPort(Inventory existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            return Optional.of(existing);
        }

        @Override
        public Inventory save(Transaction tx, Inventory inv) {
            this.saved = inv;
            return inv; // Fake: không mô phỏng gán ID thật (đó là việc PostgresInventoryAdapter, test riêng)
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldGrantItem_whenEntryAlwaysDrops() {
        // dropChancePercent=100 -> luôn rơi, tránh flaky theo random thật (LootRoller dùng
        // ThreadLocalRandom, không mock được — chọn tỉ lệ chắc chắn thay vì Fake random).
        List<DropTableEntry> entries = List.of(new DropTableEntry(100L, 100, 2, 2));
        FakeDropTablePort dropTablePort = new FakeDropTablePort(entries);
        Inventory inventory = new Inventory(1L, 10, List.of());
        FakeInventoryPort inventoryPort = new FakeInventoryPort(inventory);
        GrantLootUseCase useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        List<ItemStack> granted = useCase.execute(1L, "any_code");

        assertThat(granted).containsExactly(ItemStack.newItem(100L, 2, 0));
        assertThat(inventoryPort.saved).isNotNull();
        assertThat(inventoryPort.saved.items()).containsExactly(ItemStack.newItem(100L, 2, 0));
    }

    @Test
    void shouldGrantNothing_whenEntryNeverDrops() {
        List<DropTableEntry> entries = List.of(new DropTableEntry(100L, 0, 1, 1));
        FakeDropTablePort dropTablePort = new FakeDropTablePort(entries);
        Inventory inventory = new Inventory(1L, 10, List.of());
        FakeInventoryPort inventoryPort = new FakeInventoryPort(inventory);
        GrantLootUseCase useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        List<ItemStack> granted = useCase.execute(1L, "any_code");

        assertThat(granted).isEmpty();
        assertThat(inventoryPort.saved).isNull(); // không rơi gì -> không cần lưu
    }

    @Test
    void shouldReturnRealIdFromSavedInventory_notThePrePersistPlaceholder() {
        // Đúng mục đích của lát cắt "trả ID thật về client": granted phải lấy từ Inventory mà
        // save() TRẢ VỀ (có id thật), không phải item id=0 (NEW_ITEM_ID) lúc add() vào bộ nhớ.
        List<DropTableEntry> entries = List.of(new DropTableEntry(100L, 100, 2, 2));
        FakeDropTablePort dropTablePort = new FakeDropTablePort(entries);
        Inventory inventory = new Inventory(1L, 10, List.of());
        InventoryPort inventoryPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(inventory);
            }

            @Override
            public Inventory save(Transaction tx, Inventory inv) {
                // Mô phỏng DB gán ID thật (999L) cho item mới, giống PostgresInventoryAdapter thật.
                List<ItemStack> withRealId = inv.items().stream()
                        .map(stack ->
                                new ItemStack(999L, stack.itemTemplateId(), stack.quantity(), stack.enhanceLevel()))
                        .toList();
                return new Inventory(inv.characterId(), inv.capacity(), withRealId);
            }
        };
        GrantLootUseCase useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        List<ItemStack> granted = useCase.execute(1L, "any_code");

        assertThat(granted).hasSize(1);
        assertThat(granted.get(0).id()).isEqualTo(999L);
        assertThat(granted.get(0).isPersisted()).isTrue();
    }

    @Test
    void shouldLoseItem_whenBagFullMidway() {
        // Túi CHỈ CÒN 1 chỗ trống, 2 entry cùng luôn rơi -> item thứ 2 bị mất (feature-inventory/
        // CLAUDE.md ghi rõ đây là hạn chế tạm thời, chưa có ground-item để trả lại).
        List<DropTableEntry> entries =
                List.of(new DropTableEntry(100L, 100, 1, 1), new DropTableEntry(200L, 100, 1, 1));
        FakeDropTablePort dropTablePort = new FakeDropTablePort(entries);
        Inventory inventory = new Inventory(1L, 1, List.of());
        FakeInventoryPort inventoryPort = new FakeInventoryPort(inventory);
        GrantLootUseCase useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        List<ItemStack> granted = useCase.execute(1L, "any_code");

        assertThat(granted).hasSize(1);
    }
}
