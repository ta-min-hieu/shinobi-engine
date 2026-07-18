// TEMPLATE — Test application layer với FAKE Port (không Mockito). Copy cùng UseCase.
package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AddItemUseCaseTest {

    /** Fake = implementation in-memory THẬT, phát hiện lỗi tương tác thật hơn Mockito ([TESTING §4]). */
    static final class FakeInventoryPort implements InventoryPort {
        Inventory saved;
        private final Inventory existing;
        FakeInventoryPort(Inventory existing) { this.existing = existing; }
        public Optional<Inventory> findByCharacter(long id) { return Optional.of(existing); }
        public void save(Transaction tx, Inventory inv) { this.saved = inv; }
    }

    /** Fake transaction manager — chạy thẳng, không cần DB. */
    static final TransactionManager DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldAddItem_whenBagHasSpace() {
        var inv = new Inventory(1L, 10, List.of());
        var port = new FakeInventoryPort(inv);
        var useCase = new AddItemUseCase(port, DIRECT_TX);

        var result = useCase.execute(1L, new ItemStack(100L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.OK);
        assertThat(port.saved).isNotNull();
    }

    @Test
    void shouldReturnFull_whenBagIsFull() {
        var full = new Inventory(1L, 1, List.of(new ItemStack(100L, 1, 0)));
        var port = new FakeInventoryPort(full);
        var useCase = new AddItemUseCase(port, DIRECT_TX);

        var result = useCase.execute(1L, new ItemStack(101L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.FULL);
        assertThat(port.saved).isNull();          // túi đầy → không lưu
    }
}
