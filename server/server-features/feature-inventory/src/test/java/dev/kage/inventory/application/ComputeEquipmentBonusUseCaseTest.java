package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongFunction;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.Equipment;
import dev.kage.inventory.domain.EquipmentBonus;
import dev.kage.inventory.domain.EquipmentPort;
import dev.kage.inventory.domain.EquipmentSlotType;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.Transaction;

class ComputeEquipmentBonusUseCaseTest {

    private static final long CHARACTER_ID = 1L;

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

    private static ItemTemplatePort fakeItemTemplatePort(LongFunction<Optional<ItemTemplate>> findById) {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return findById.apply(itemTemplateId);
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                throw new UnsupportedOperationException("Test này không cần tra theo code");
            }
        };
    }

    @Test
    void shouldSumBonusAtk_fromAllEquippedItems() {
        ItemStack weapon = new ItemStack(1L, 100L, 1, 0);
        ItemStack armor = new ItemStack(2L, 200L, 1, 0);
        InventoryPort inventoryPort = fakeInventoryPort(List.of(weapon, armor));
        EquipmentPort equipmentPort =
                fakeEquipmentPort(Map.of(EquipmentSlotType.WEAPON, 1L, EquipmentSlotType.ARMOR, 2L));
        ItemTemplatePort itemTemplatePort = fakeItemTemplatePort(itemTemplateId -> switch ((int) itemTemplateId) {
            case 100 -> Optional.of(new ItemTemplate(100L, EquipmentSlotType.WEAPON, 1, 5, 0, 0, 0, 1));
            case 200 -> Optional.of(new ItemTemplate(200L, EquipmentSlotType.ARMOR, 1, 3, 0, 0, 0, 1));
            default -> Optional.empty();
        });
        ComputeEquipmentBonusUseCase useCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);

        EquipmentBonus bonus = useCase.execute(CHARACTER_ID);

        assertThat(bonus.bonusAtk()).isEqualTo(8);
    }

    @Test
    void shouldReturnZero_whenNothingEquipped() {
        InventoryPort inventoryPort = fakeInventoryPort(List.of());
        EquipmentPort equipmentPort = fakeEquipmentPort(Map.of());
        ItemTemplatePort itemTemplatePort = fakeItemTemplatePort(itemTemplateId -> Optional.empty());
        ComputeEquipmentBonusUseCase useCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);

        EquipmentBonus bonus = useCase.execute(CHARACTER_ID);

        assertThat(bonus.bonusAtk()).isZero();
    }

    @Test
    void shouldSkipStaleReference_whenEquippedItemNoLongerInInventory() {
        // equipment_slot trỏ tới item ĐÃ không còn trong túi (dữ liệu lệch hiếm gặp) — bỏ qua an
        // toàn thay vì ném lỗi, không được để 1 dòng lệch làm hỏng toàn bộ tính chỉ số.
        InventoryPort inventoryPort = fakeInventoryPort(List.of());
        EquipmentPort equipmentPort = fakeEquipmentPort(Map.of(EquipmentSlotType.WEAPON, 999L));
        ItemTemplatePort itemTemplatePort = fakeItemTemplatePort(itemTemplateId -> Optional.empty());
        ComputeEquipmentBonusUseCase useCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);

        EquipmentBonus bonus = useCase.execute(CHARACTER_ID);

        assertThat(bonus.bonusAtk()).isZero();
    }

    @Test
    void shouldSumAllFourStats_fromAllEquippedItems() {
        // ADR-036 D1/D2 — trước ADR này chỉ có bonusAtk chạy được; giáp cộng DEF/RES/HP hoàn toàn
        // không có đường nào tới server-world.
        ItemStack weapon = new ItemStack(1L, 100L, 1, 0);
        ItemStack armor = new ItemStack(2L, 200L, 1, 0);
        InventoryPort inventoryPort = fakeInventoryPort(List.of(weapon, armor));
        EquipmentPort equipmentPort =
                fakeEquipmentPort(Map.of(EquipmentSlotType.WEAPON, 1L, EquipmentSlotType.ARMOR, 2L));
        ItemTemplatePort itemTemplatePort = fakeItemTemplatePort(itemTemplateId -> switch ((int) itemTemplateId) {
            case 100 -> Optional.of(new ItemTemplate(100L, EquipmentSlotType.WEAPON, 1, 18, 0, 0, 0, 5));
            case 200 -> Optional.of(new ItemTemplate(200L, EquipmentSlotType.ARMOR, 1, 0, 10, 6, 20, 5));
            default -> Optional.empty();
        });
        ComputeEquipmentBonusUseCase useCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);

        EquipmentBonus bonus = useCase.execute(CHARACTER_ID);

        assertThat(bonus).isEqualTo(new EquipmentBonus(18, 10, 6, 20));
    }

    @Test
    void shouldScaleEachItemByItsOwnEnhanceLevel() {
        // ADR-036 D3 — CHÍNH LÀ BUG ĐƯỢC SỬA: trước đó enhance_level không được đọc ở bất kỳ công
        // thức chỉ số nào, cường hoá +7 chỉ đổi một nhãn trong tên hiển thị (đo bằng chơi thật:
        // steel_katana +0→+7 trừ 8.900 vàng, chỉ số không đổi).
        //
        // Hai món CÙNG lúc với hai cấp cường hoá KHÁC nhau — chứng minh hệ số áp cho TỪNG món chứ
        // không phải cho tổng: vũ khí +5 ⇒ 20 × 1,5 = 30; giáp +0 ⇒ 10 DEF nguyên vẹn.
        ItemStack weapon = new ItemStack(1L, 100L, 1, 5);
        ItemStack armor = new ItemStack(2L, 200L, 1, 0);
        InventoryPort inventoryPort = fakeInventoryPort(List.of(weapon, armor));
        EquipmentPort equipmentPort =
                fakeEquipmentPort(Map.of(EquipmentSlotType.WEAPON, 1L, EquipmentSlotType.ARMOR, 2L));
        ItemTemplatePort itemTemplatePort = fakeItemTemplatePort(itemTemplateId -> switch ((int) itemTemplateId) {
            case 100 -> Optional.of(new ItemTemplate(100L, EquipmentSlotType.WEAPON, 1, 20, 0, 0, 0, 1));
            case 200 -> Optional.of(new ItemTemplate(200L, EquipmentSlotType.ARMOR, 1, 0, 10, 0, 0, 1));
            default -> Optional.empty();
        });
        ComputeEquipmentBonusUseCase useCase =
                new ComputeEquipmentBonusUseCase(equipmentPort, inventoryPort, itemTemplatePort);

        EquipmentBonus bonus = useCase.execute(CHARACTER_ID);

        assertThat(bonus).isEqualTo(new EquipmentBonus(30, 10, 0, 0));
    }
}
