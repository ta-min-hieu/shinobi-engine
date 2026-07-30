package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Transaction;
import dev.kage.inventory.domain.TransactionRunner;

class UnequipItemUseCaseTest {

    private static final long CHARACTER_ID = 1L;
    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

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

    @Test
    void shouldUnequip_whenSlotOccupied() {
        FakeEquipmentPort equipmentPort =
                new FakeEquipmentPort(new Equipment(CHARACTER_ID, Map.of(EquipmentSlotType.WEAPON, 42L)));
        UnequipItemUseCase useCase = new UnequipItemUseCase(equipmentPort, DIRECT_TX);

        UnequipItemUseCase.UnequipItemResult result = useCase.execute(CHARACTER_ID, EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(UnequipItemUseCase.UnequipItemResult.OK);
        assertThat(equipmentPort.saved.equippedItemId(EquipmentSlotType.WEAPON)).isEmpty();
    }

    @Test
    void shouldReportNotEquipped_whenSlotAlreadyEmpty_andNotSave() {
        FakeEquipmentPort equipmentPort = new FakeEquipmentPort(new Equipment(CHARACTER_ID, Map.of()));
        UnequipItemUseCase useCase = new UnequipItemUseCase(equipmentPort, DIRECT_TX);

        UnequipItemUseCase.UnequipItemResult result = useCase.execute(CHARACTER_ID, EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(UnequipItemUseCase.UnequipItemResult.NOT_EQUIPPED);
        assertThat(equipmentPort.saved).isNull(); // không đổi gì → không cần lưu
    }
}
