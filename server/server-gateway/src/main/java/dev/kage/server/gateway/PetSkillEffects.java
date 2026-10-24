package dev.kage.server.gateway;

import java.util.Optional;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.inventory.domain.PetSkill;
import dev.kage.server.world.EquipmentBonusSnapshot;

/**
 * Phụ phẩm của trang bị quy ra server-world: tốc độ phi kiếm (ADR-070) + kỹ năng linh thú đang mặc (ADR-086). Chạm DB —
 * chỉ gọi trên Virtual Thread.
 */
final class PetSkillEffects {

    private PetSkillEffects() {}

    static EquipmentBonusSnapshot.Extras extrasOf(InventoryFacade inventory, long characterId) {
        int speed = inventory.moveSpeedPercent(characterId);
        Optional<PetProgress> pet = inventory.equippedPet(characterId);
        if (pet.isEmpty()) {
            return EquipmentBonusSnapshot.Extras.speed(speed);
        }
        Optional<PetSkill> skill = inventory.itemCatalog().entries().stream()
                .filter(entry -> entry.id() == pet.get().itemTemplateId())
                .findFirst()
                .map(ItemCatalogEntry::code)
                .flatMap(PetSkill::ofPet);
        int chance = PetSkill.chancePermille(pet.get().stage());
        return new EquipmentBonusSnapshot.Extras(
                speed,
                skill.filter(PetSkill.STRIKE::equals).isPresent() ? chance : 0,
                skill.filter(PetSkill.GUARD::equals).isPresent() ? chance : 0);
    }
}
