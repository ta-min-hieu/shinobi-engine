package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AddItemUseCaseTest {

    /** Fake = implementation in-memory THẬT, phát hiện lỗi tương tác thật hơn Mockito (TESTING.md §4). */
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
            return inv;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldAddItem_whenBagHasSpace() {
        Inventory inv = new Inventory(1L, 10, List.of());
        FakeInventoryPort port = new FakeInventoryPort(inv);
        AddItemUseCase useCase = new AddItemUseCase(port, DIRECT_TX);

        AddItemUseCase.AddItemResult result = useCase.execute(1L, ItemStack.newItem(100L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.OK);
        assertThat(port.saved).isNotNull();
    }

    @Test
    void shouldReturnFull_whenBagIsFull() {
        Inventory full = new Inventory(1L, 1, List.of(ItemStack.newItem(100L, 1, 0)));
        FakeInventoryPort port = new FakeInventoryPort(full);
        AddItemUseCase useCase = new AddItemUseCase(port, DIRECT_TX);

        AddItemUseCase.AddItemResult result = useCase.execute(1L, ItemStack.newItem(101L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.FULL);
        assertThat(port.saved).isNull(); // túi đầy → không lưu
    }
}
