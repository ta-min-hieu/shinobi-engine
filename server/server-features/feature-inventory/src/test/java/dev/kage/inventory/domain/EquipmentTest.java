package dev.kage.inventory.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class EquipmentTest {

    @Test
    void shouldEquipItem_intoEmptySlot() {
        var equipment = new Equipment(1L, Map.of());

        equipment.equip(EquipmentSlotType.WEAPON, 42L);

        assertThat(equipment.equippedItemId(EquipmentSlotType.WEAPON)).contains(42L);
    }

    @Test
    void shouldReplaceOldItem_whenEquippingIntoOccupiedSlot() {
        var equipment = new Equipment(1L, Map.of(EquipmentSlotType.WEAPON, 1L));

        equipment.equip(EquipmentSlotType.WEAPON, 2L);

        assertThat(equipment.equippedItemId(EquipmentSlotType.WEAPON)).contains(2L);
    }

    @Test
    void shouldUnequip_whenSlotOccupied() {
        var equipment = new Equipment(1L, Map.of(EquipmentSlotType.WEAPON, 42L));

        var result = equipment.unequip(EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(Equipment.UnequipResult.UNEQUIPPED);
        assertThat(equipment.equippedItemId(EquipmentSlotType.WEAPON)).isEmpty();
    }

    @Test
    void shouldReportNotEquipped_whenSlotAlreadyEmpty() {
        var equipment = new Equipment(1L, Map.of());

        var result = equipment.unequip(EquipmentSlotType.WEAPON);

        assertThat(result).isEqualTo(Equipment.UnequipResult.NOT_EQUIPPED);
    }
}
