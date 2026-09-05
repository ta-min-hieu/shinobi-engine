package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

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

    /** Mọi item trong test này là TRANG BỊ (maxStack = 1) trừ khi test nói khác — không gộp chồng. */
    private static final ItemTemplatePort NON_STACKABLE = templatePortWithMaxStack(1);

    /** Tiêu hao: maxStack = 99, gộp được. */
    private static final ItemTemplatePort STACKABLE = templatePortWithMaxStack(99);

    /** {@link ItemTemplatePort} có 2 method ⇒ không phải functional interface, phải dựng tay. */
    private static ItemTemplatePort templatePortWithMaxStack(int maxStack) {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return Optional.of(new ItemTemplate(
                        itemTemplateId, maxStack == 1 ? EquipmentSlotType.WEAPON : null, maxStack, 0, 0, 0, 0, 1));
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }
        };
    }

    @Test
    void shouldAddItem_whenBagHasSpace() {
        Inventory inv = new Inventory(1L, 10, List.of());
        FakeInventoryPort port = new FakeInventoryPort(inv);
        AddItemUseCase useCase = new AddItemUseCase(port, NON_STACKABLE, DIRECT_TX);

        AddItemUseCase.AddItemResult result = useCase.execute(1L, ItemStack.newItem(100L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.OK);
        assertThat(port.saved).isNotNull();
    }

    @Test
    void shouldNotConsumeASlot_whenStackableItemMergesIntoFullBag() {
        // Túi ĐẦY nhưng item gộp được vào chồng sẵn có ⇒ vẫn OK, không cần ô mới.
        Inventory full = new Inventory(1L, 1, List.of(new ItemStack(9L, 100L, 3, 0)));
        FakeInventoryPort port = new FakeInventoryPort(full);
        AddItemUseCase useCase = new AddItemUseCase(port, STACKABLE, DIRECT_TX);

        AddItemUseCase.AddItemResult result = useCase.execute(1L, ItemStack.newItem(100L, 2, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.OK);
        assertThat(port.saved.items()).hasSize(1);
        assertThat(port.saved.items().get(0).quantity()).isEqualTo(5);
    }

    @Test
    void shouldReturnFull_whenBagIsFull() {
        Inventory full = new Inventory(1L, 1, List.of(ItemStack.newItem(100L, 1, 0)));
        FakeInventoryPort port = new FakeInventoryPort(full);
        AddItemUseCase useCase = new AddItemUseCase(port, NON_STACKABLE, DIRECT_TX);

        AddItemUseCase.AddItemResult result = useCase.execute(1L, ItemStack.newItem(101L, 1, 0));

        assertThat(result).isEqualTo(AddItemUseCase.AddItemResult.FULL);
        assertThat(port.saved).isNull(); // túi đầy → không lưu
    }
}
