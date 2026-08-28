package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.InventorySnapshot;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.Transaction;

/** ADR-035 Decision-1 — ảnh chụp túi đồ + trang bị trong CÙNG một lời gọi. */
class ListInventoryUseCaseTest {

    private static final long CHARACTER_ID = 7L;

    private static InventoryPort fakeInventoryPort(List<ItemStack> items) {
        return new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.of(new Inventory(characterId, 20, items));
            }

            @Override
            public Inventory save(Transaction tx, Inventory inventory) {
                return inventory;
            }
        };
    }

    private static EquipmentPort fakeEquipmentPort(Map<EquipmentSlotType, Long> slots) {
        return new EquipmentPort() {
            @Override
            public Optional<Equipment> findByCharacter(long characterId) {
                return Optional.of(new Equipment(characterId, slots));
            }

            @Override
            public void save(Transaction tx, Equipment equipment) {}
        };
    }

    @Test
    void shouldReturnItemsAndEquippedSlotsTogether() {
        ItemStack sword = new ItemStack(42L, 2L, 1, 3);
        ItemStack kunai = new ItemStack(43L, 1L, 5, 0);
        ListInventoryUseCase useCase = new ListInventoryUseCase(
                fakeInventoryPort(List.of(sword, kunai)), fakeEquipmentPort(Map.of(EquipmentSlotType.WEAPON, 42L)));

        InventorySnapshot snapshot = useCase.execute(CHARACTER_ID);

        assertThat(snapshot.inventory().capacity()).isEqualTo(20);
        assertThat(snapshot.inventory().items()).containsExactly(sword, kunai);
        // Item ĐANG MẶC vẫn nằm trong túi (equipment_slot chỉ giữ tham chiếu) — client cần cả hai
        // để vẽ được cùng lúc "ô túi" và "ô trang bị" mà không đoán.
        assertThat(snapshot.equipment().slots()).containsExactly(Map.entry(EquipmentSlotType.WEAPON, 42L));
    }

    @Test
    void shouldReturnEmptySnapshot_whenNothingOwned() {
        ListInventoryUseCase useCase =
                new ListInventoryUseCase(fakeInventoryPort(List.of()), fakeEquipmentPort(Map.of()));

        InventorySnapshot snapshot = useCase.execute(CHARACTER_ID);

        // Túi RỖNG là trạng thái bình thường, không phải lỗi — capacity vẫn phải có để vẽ ô trống.
        assertThat(snapshot.inventory().items()).isEmpty();
        assertThat(snapshot.equipment().slots()).isEmpty();
        assertThat(snapshot.inventory().capacity()).isEqualTo(20);
    }

    @Test
    void shouldThrow_whenInventoryPortReturnsEmpty() {
        InventoryPort brokenPort = new InventoryPort() {
            @Override
            public Optional<Inventory> findByCharacter(long characterId) {
                return Optional.empty(); // vi phạm hợp đồng InventoryPort — phải nổ, không trả rỗng ngầm
            }

            @Override
            public Inventory save(Transaction tx, Inventory inventory) {
                return inventory;
            }
        };
        ListInventoryUseCase useCase = new ListInventoryUseCase(brokenPort, fakeEquipmentPort(Map.of()));

        assertThatThrownBy(() -> useCase.execute(CHARACTER_ID)).isInstanceOf(CharacterInventoryNotFoundException.class);
    }
}
