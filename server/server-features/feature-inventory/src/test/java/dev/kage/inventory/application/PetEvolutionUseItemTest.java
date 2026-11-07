package dev.kage.inventory.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import dev.kage.inventory.domain.CompanionPort;
import dev.kage.inventory.domain.Inventory;
import dev.kage.inventory.domain.InventoryPort;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemTemplate;
import dev.kage.inventory.domain.ItemTemplatePort;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.inventory.domain.PetPolicy;
import dev.kage.inventory.domain.PetProgress;
import dev.kage.inventory.domain.Transaction;

/** ADR-078 — Linh Thú Đan tiến hoá linh thú đang mặc; từ chối thì KHÔNG mất viên đan. */
class PetEvolutionUseItemTest {

    private static final long CHARACTER_ID = 7L;
    private static final long PILL_ITEM_ID = 41L;
    private static final long PILL_TEMPLATE_ID = 901L;
    private static final long PET_ITEM_ID = 55L;

    private static final class FakeInventoryPort implements InventoryPort {
        private final Inventory existing = new Inventory(
                CHARACTER_ID, 20, new ArrayList<>(List.of(new ItemStack(PILL_ITEM_ID, PILL_TEMPLATE_ID, 2, 0))));
        Inventory saved;

        @Override
        public Optional<Inventory> findByCharacter(long characterId) {
            return Optional.of(existing);
        }

        @Override
        public Inventory save(Transaction tx, Inventory inventory) {
            saved = inventory;
            return inventory;
        }
    }

    /** Một linh thú (hoặc không) với cấp/bậc cho trước; ghi lại bậc được set. */
    private static final class FakeCompanions implements CompanionPort {
        private final Optional<PetProgress> pet;
        Integer stageSet;
        Long expGranted;

        FakeCompanions(Optional<PetProgress> pet) {
            this.pet = pet;
        }

        @Override
        public int moveSpeedPercentOf(long itemTemplateId) {
            return 0;
        }

        @Override
        public Map<Long, Integer> petLevels(long characterId) {
            return Map.of();
        }

        @Override
        public Optional<PetProgress> grantPetExp(long characterId, long amount) {
            return Optional.empty();
        }

        @Override
        public Optional<PetProgress> equippedPet(long characterId) {
            return pet;
        }

        @Override
        public Map<Long, Integer> petStages(long characterId) {
            return Map.of();
        }

        @Override
        public Optional<PetProgress> lockEquippedPet(Transaction tx, long characterId) {
            return pet;
        }

        @Override
        public void setPetStage(Transaction tx, long inventoryItemId, int stage) {
            stageSet = stage;
        }

        @Override
        public Optional<PetProgress> grantPetExp(Transaction tx, long characterId, long amount) {
            expGranted = amount;
            return pet;
        }

        @Override
        public Optional<Long> wornMountItemId(Transaction tx, long characterId) {
            return Optional.empty();
        }
    }

    private static ItemTemplatePort catalog() {
        return catalog(ItemUseEffect.EVOLVE_PET, 1);
    }

    private static ItemTemplatePort catalog(ItemUseEffect effect, int useValue) {
        return new ItemTemplatePort() {
            @Override
            public Optional<ItemTemplate> findById(long itemTemplateId) {
                return itemTemplateId == PILL_TEMPLATE_ID
                        ? Optional.of(new ItemTemplate(PILL_TEMPLATE_ID, null, 10, 0, 0, 0, 0, 1, effect, useValue))
                        : Optional.empty();
            }

            @Override
            public Optional<Long> findIdByCode(String code) {
                return Optional.empty();
            }
        };
    }

    private static PetProgress pet(int level, int stage) {
        return new PetProgress(PET_ITEM_ID, 1L, level, 0L, false, stage);
    }

    private static UseItemUseCase.UseItemResult use(FakeInventoryPort inventory, FakeCompanions companions) {
        return use(inventory, companions, catalog());
    }

    private static UseItemUseCase.UseItemResult use(
            FakeInventoryPort inventory, FakeCompanions companions, ItemTemplatePort catalog) {
        return new UseItemUseCase(
                        new UseItemUseCase.Ports(inventory, catalog, (tx, c, a, d) -> {}, (tx, c, i) -> 0, companions),
                        action -> action.accept(null))
                .execute(CHARACTER_ID, PILL_ITEM_ID);
    }

    /** ADR-201 — thức ăn linh thú: cộng EXP và trừ một món; không mặc / cấp tối đa thì không trừ. */
    @Test
    void foodShouldFeedTheEquippedPet_orRefuseWithoutConsuming() {
        FakeInventoryPort inventory = new FakeInventoryPort();
        FakeCompanions companions = new FakeCompanions(Optional.of(pet(5, 1)));
        UseItemUseCase.UseItemResult fed = use(inventory, companions, catalog(ItemUseEffect.FEED_PET, 200));
        assertThat(fed.outcome()).isEqualTo(UseItemUseCase.Outcome.OK);
        assertThat(fed.remainingQuantity()).isEqualTo(1);
        assertThat(companions.expGranted).isEqualTo(200L);

        FakeInventoryPort none = new FakeInventoryPort();
        assertThat(use(none, new FakeCompanions(Optional.empty()), catalog(ItemUseEffect.FEED_PET, 200))
                        .outcome())
                .isEqualTo(UseItemUseCase.Outcome.NO_PET_EQUIPPED);
        assertThat(none.saved).isNull();
        FakeInventoryPort max = new FakeInventoryPort();
        FakeCompanions maxed = new FakeCompanions(Optional.of(pet(PetPolicy.MAX_LEVEL, 1)));
        assertThat(use(max, maxed, catalog(ItemUseEffect.FEED_PET, 200)).outcome())
                .isEqualTo(UseItemUseCase.Outcome.PET_AT_MAX_LEVEL);
        assertThat(max.saved).isNull();
        assertThat(maxed.expGranted).isNull();
    }

    @Test
    void shouldEvolveTheEquippedPet_andConsumeOnePill() {
        FakeInventoryPort inventory = new FakeInventoryPort();
        FakeCompanions companions = new FakeCompanions(Optional.of(pet(PetPolicy.EVOLVE_LEVEL, 1)));

        UseItemUseCase.UseItemResult result = use(inventory, companions);

        assertThat(result.outcome()).isEqualTo(UseItemUseCase.Outcome.OK);
        assertThat(result.effect()).isEqualTo(ItemUseEffect.EVOLVE_PET);
        assertThat(result.remainingQuantity()).isEqualTo(1);
        assertThat(companions.stageSet).isEqualTo(2);
    }

    @Test
    void shouldRefuseWithoutConsuming_whenNoPetIsEquipped_tooLow_orAlreadyEvolved() {
        assertRefused(Optional.empty(), UseItemUseCase.Outcome.NO_PET_EQUIPPED);
        assertRefused(Optional.of(pet(PetPolicy.EVOLVE_LEVEL - 1, 1)), UseItemUseCase.Outcome.PET_LEVEL_TOO_LOW);
        assertRefused(
                Optional.of(pet(PetPolicy.MAX_LEVEL, PetPolicy.MAX_STAGE)), UseItemUseCase.Outcome.PET_ALREADY_EVOLVED);
    }

    private static void assertRefused(Optional<PetProgress> pet, UseItemUseCase.Outcome expected) {
        FakeInventoryPort inventory = new FakeInventoryPort();
        FakeCompanions companions = new FakeCompanions(pet);

        UseItemUseCase.UseItemResult result = use(inventory, companions);

        assertThat(result.outcome()).isEqualTo(expected);
        assertThat(inventory.saved).as("không lưu túi = không mất viên đan").isNull();
        assertThat(companions.stageSet).isNull();
    }

    @Test
    void evolvedStageShouldMultiplyPetStatsByOneAndAHalf_roundingDown() {
        assertThat(PetPolicy.staged(10, 1)).isEqualTo(10);
        assertThat(PetPolicy.staged(10, 2)).isEqualTo(15);
        assertThat(PetPolicy.staged(3, 2)).isEqualTo(4);
    }

    /** Hồi quy 2026-10-24: linh thú cấp 17..30 từng làm tính chỉ số ném lỗi (bảng cường hoá chặn ở +15). */
    @Test
    void petBonusShouldScaleAllTheWayToMaxLevel() {
        assertThat(PetPolicy.petBonus(10, 1, 1)).isEqualTo(10);
        assertThat(PetPolicy.petBonus(10, 20, 1)).isEqualTo(29);
        assertThat(PetPolicy.petBonus(10, PetPolicy.MAX_LEVEL, 1)).isEqualTo(39);
        assertThat(PetPolicy.petBonus(10, PetPolicy.MAX_LEVEL, 2)).isEqualTo(58);
    }
}
