package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

class EquipItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final long WEAPON_ITEM_ID = 42L;
    private static final long WEAPON_TEMPLATE_ID = 100L;

    static final class FakeInventoryPort implements InventoryPort {
        private final Inventory existing;

        FakeInventoryPort(Inventory existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            return Optional.of(existing);
        }

        @Override
        public Inventory save(Transaction tx, Inventory inventory) {
            return inventory;
        }
    }

    static final class FakeItemTemplatePort implements ItemTemplatePort {
        private final Map<Long, ItemTemplate> templates;

        FakeItemTemplatePort(Map<Long, ItemTemplate> templates) {
            this.templates = templates;
        }

        @Override
        public Optional<ItemTemplate> findById(long itemTemplateId) {
            return Optional.ofNullable(templates.get(itemTemplateId));
        }

        @Override
        public Optional<Long> findIdByCode(String code) {
            throw new UnsupportedOperationException("Test này không cần tra theo code");
        }
    }

    static final class FakeEquipmentPort implements EquipmentPort {
        Equipment saved;
        private final Equipment existing;

        FakeEquipmentPort(Equipment existing) {
            this.existing = existing;
        }

        @Override
        public Optional<Equipment> findByCharacter(long characterId) {
            return Optional.of(existing);
        }

        @Override
        public void save(Transaction tx, Equipment equipment) {
            this.saved = equipment;
        }
    }

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    @Test
    void shouldEquip_whenItemBelongsToCharacterAndMatchesSlot() {
        FakeInventoryPort inventoryPort = new FakeInventoryPort(
                new Inventory(CHARACTER_ID, 20, List.of(new ItemStack(WEAPON_ITEM_ID, WEAPON_TEMPLATE_ID, 1, 0))));
        FakeItemTemplatePort templatePort = new FakeItemTemplatePort(
                Map.of(WEAPON_TEMPLATE_ID, new ItemTemplate(WEAPON_TEMPLATE_ID, EquipmentSlotType.WEAPON, 5)));
        FakeEquipmentPort equipmentPort = new FakeEquipmentPort(new Equipment(CHARACTER_ID, Map.of()));
        EquipItemUseCase useCase = new EquipItemUseCase(inventoryPort, templatePort, equipmentPort, DIRECT_TX);

        EquipItemUseCase.EquipItemResult result =
                useCase.execute(CHARACTER_ID, WEAPON_ITEM_ID, EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(EquipItemUseCase.EquipItemResult.OK);
        assertThat(equipmentPort.saved.equippedItemId(EquipmentSlotType.WEAPON)).contains(WEAPON_ITEM_ID);
    }

    @Test
    void shouldRejectEquip_whenItemDoesNotBelongToCharacter() {
        FakeInventoryPort inventoryPort = new FakeInventoryPort(new Inventory(CHARACTER_ID, 20, List.of())); // túi rỗng
        FakeItemTemplatePort templatePort = new FakeItemTemplatePort(Map.of());
        FakeEquipmentPort equipmentPort = new FakeEquipmentPort(new Equipment(CHARACTER_ID, Map.of()));
        EquipItemUseCase useCase = new EquipItemUseCase(inventoryPort, templatePort, equipmentPort, DIRECT_TX);

        EquipItemUseCase.EquipItemResult result = useCase.execute(CHARACTER_ID, 999L, EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(EquipItemUseCase.EquipItemResult.ITEM_NOT_FOUND);
        assertThat(equipmentPort.saved).isNull();
    }

    @Test
    void shouldRejectEquip_whenItemDoesNotMatchTargetSlot() {
        // Item là WEAPON nhưng người chơi cố mặc vào slot ARMOR — server tự kiểm tra, không tin client.
        FakeInventoryPort inventoryPort = new FakeInventoryPort(
                new Inventory(CHARACTER_ID, 20, List.of(new ItemStack(WEAPON_ITEM_ID, WEAPON_TEMPLATE_ID, 1, 0))));
        FakeItemTemplatePort templatePort = new FakeItemTemplatePort(
                Map.of(WEAPON_TEMPLATE_ID, new ItemTemplate(WEAPON_TEMPLATE_ID, EquipmentSlotType.WEAPON, 5)));
        FakeEquipmentPort equipmentPort = new FakeEquipmentPort(new Equipment(CHARACTER_ID, Map.of()));
        EquipItemUseCase useCase = new EquipItemUseCase(inventoryPort, templatePort, equipmentPort, DIRECT_TX);

        EquipItemUseCase.EquipItemResult result =
                useCase.execute(CHARACTER_ID, WEAPON_ITEM_ID, EquipmentSlotType.ARMOR);

        assertThat(result).isEqualTo(EquipItemUseCase.EquipItemResult.WRONG_SLOT);
        assertThat(equipmentPort.saved).isNull();
    }
}
