package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.inventory.domain.DropTableEntry;
import dev.kage.inventory.domain.DropTablePort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

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
        public void save(Transaction tx, Inventory inv) {
            this.saved = inv;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldGrantItem_whenEntryAlwaysDrops() {
        // dropChancePercent=100 -> luôn rơi, tránh flaky theo random thật (LootRoller dùng
        // ThreadLocalRandom, không mock được — chọn tỉ lệ chắc chắn thay vì Fake random).
        var entries = List.of(new DropTableEntry(100L, 100, 2, 2));
        var dropTablePort = new FakeDropTablePort(entries);
        var inventory = new Inventory(1L, 10, List.of());
        var inventoryPort = new FakeInventoryPort(inventory);
        var useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        var granted = useCase.execute(1L, "any_code");

        assertThat(granted).containsExactly(new ItemStack(100L, 2, 0));
        assertThat(inventoryPort.saved).isNotNull();
        assertThat(inventoryPort.saved.items()).containsExactly(new ItemStack(100L, 2, 0));
    }

    @Test
    void shouldGrantNothing_whenEntryNeverDrops() {
        var entries = List.of(new DropTableEntry(100L, 0, 1, 1));
        var dropTablePort = new FakeDropTablePort(entries);
        var inventory = new Inventory(1L, 10, List.of());
        var inventoryPort = new FakeInventoryPort(inventory);
        var useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        var granted = useCase.execute(1L, "any_code");

        assertThat(granted).isEmpty();
        assertThat(inventoryPort.saved).isNull(); // không rơi gì -> không cần lưu
    }

    @Test
    void shouldLoseItem_whenBagFullMidway() {
        // Túi CHỈ CÒN 1 chỗ trống, 2 entry cùng luôn rơi -> item thứ 2 bị mất (feature-inventory/
        // CLAUDE.md ghi rõ đây là hạn chế tạm thời, chưa có ground-item để trả lại).
        var entries = List.of(
                new DropTableEntry(100L, 100, 1, 1),
                new DropTableEntry(200L, 100, 1, 1));
        var dropTablePort = new FakeDropTablePort(entries);
        var inventory = new Inventory(1L, 1, List.of());
        var inventoryPort = new FakeInventoryPort(inventory);
        var useCase = new GrantLootUseCase(dropTablePort, inventoryPort, DIRECT_TX);

        var granted = useCase.execute(1L, "any_code");

        assertThat(granted).hasSize(1);
    }
}
