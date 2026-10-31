package dev.kage.crafting.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import dev.kage.crafting.domain.CraftingWorkshopPort;
import dev.kage.crafting.domain.ForgeQuality;
import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeKind;
import dev.kage.crafting.domain.RecipeMaterial;
import dev.kage.crafting.domain.Transaction;
import dev.kage.crafting.domain.TransactionRunner;

/** ADR-079 D3 — một mẻ luyện là một transaction: thiếu ở bước nào thì không bước nào còn hiệu lực. */
class CraftItemUseCaseTest {

    private static final long CHARACTER = 7L;
    private static final long HERB = 1L;
    private static final long CORE = 2L;
    private static final long PILL = 3L;
    private static final Recipe PILL_RECIPE = new Recipe(
            "healing_pill",
            PILL,
            3,
            50L,
            5,
            List.of(new RecipeMaterial(HERB, 2), new RecipeMaterial(CORE, 1)),
            RecipeKind.ALCHEMY);
    private static final long ORE = 4L;
    private static final long SWORD = 5L;
    private static final Recipe SWORD_RECIPE =
            new Recipe("iron_sword", SWORD, 1, 60L, 20, List.of(new RecipeMaterial(ORE, 3)), RecipeKind.FORGE);

    /** Túi + vàng trong bộ nhớ; transaction chụp ảnh và khôi phục khi khối lệnh ném (rollback THẬT). */
    private static final class Workshop implements CraftingWorkshopPort, TransactionRunner {
        Map<Long, Integer> bag = new HashMap<>();
        long gold;
        int freeSlots = 5;
        final List<String> audited = new ArrayList<>();
        final List<Integer> forgedLines = new ArrayList<>();

        // PMD AvoidCatchingGenericException: fake rollback phải bắt MỌI lỗi của khối lệnh rồi ném lại nguyên vẹn.
        @SuppressWarnings("PMD.AvoidCatchingGenericException")
        @Override
        public void inTransaction(Consumer<Transaction> action) {
            Map<Long, Integer> bagBefore = new HashMap<>(bag);
            long goldBefore = gold;
            try {
                action.accept(null);
            } catch (RuntimeException e) {
                bag = bagBefore;
                gold = goldBefore;
                throw e;
            }
        }

        @Override
        public void lockInventory(Transaction tx, long characterId) {}

        @Override
        public boolean consumeMaterial(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            int owned = bag.getOrDefault(itemTemplateId, 0);
            if (owned < quantity) {
                return false;
            }
            bag.put(itemTemplateId, owned - quantity);
            return true;
        }

        @Override
        public boolean deductGold(Transaction tx, long characterId, long amount) {
            if (gold < amount) {
                return false;
            }
            gold -= amount;
            return true;
        }

        @Override
        public boolean grantItem(Transaction tx, long characterId, long itemTemplateId, int quantity) {
            if (freeSlots <= 0) {
                return false;
            }
            bag.merge(itemTemplateId, quantity, Integer::sum);
            return true;
        }

        @Override
        public boolean grantForged(Transaction tx, long characterId, long itemTemplateId, int refinementLines) {
            if (freeSlots <= 0) {
                return false;
            }
            bag.merge(itemTemplateId, 1, Integer::sum);
            forgedLines.add(refinementLines);
            return true;
        }

        @Override
        public void audit(Transaction tx, long characterId, String action, String detailJson) {
            audited.add(action);
        }
    }

    private static CraftItemUseCase useCase(Workshop workshop) {
        return new CraftItemUseCase(() -> List.of(PILL_RECIPE, SWORD_RECIPE), workshop, workshop);
    }

    private static Workshop stocked() {
        Workshop workshop = new Workshop();
        workshop.bag.put(HERB, 5);
        workshop.bag.put(CORE, 1);
        workshop.bag.put(ORE, 3);
        workshop.gold = 100L;
        return workshop;
    }

    @Test
    void forgeShouldGrantOneItemWithTheRolledQualityLines() {
        Workshop workshop = stocked();

        CraftItemUseCase.ForgeResult result = useCase(workshop).forge(CHARACTER, 20, "iron_sword");

        assertThat(result.outcome()).isEqualTo(CraftItemUseCase.Outcome.OK);
        ForgeQuality quality = result.quality().orElseThrow();
        assertThat(workshop.forgedLines).containsExactly(quality.refinementLines());
        assertThat(workshop.bag).containsEntry(SWORD, 1).containsEntry(ORE, 0);
        assertThat(workshop.gold).isEqualTo(40L);
        assertThat(workshop.audited).containsExactly("FORGE_ITEM");
    }

    @Test
    void forgeAndAlchemyShouldNotAcceptEachOthersRecipes() {
        Workshop workshop = stocked();

        assertThat(useCase(workshop).forge(CHARACTER, 20, "healing_pill").outcome())
                .isEqualTo(CraftItemUseCase.Outcome.UNKNOWN_RECIPE);
        assertThat(useCase(workshop).craft(CHARACTER, 20, "iron_sword"))
                .isEqualTo(CraftItemUseCase.Outcome.UNKNOWN_RECIPE);
        assertThat(useCase(workshop).recipes(RecipeKind.FORGE)).containsExactly(SWORD_RECIPE);
        assertThat(useCase(workshop).recipes(RecipeKind.ALCHEMY)).containsExactly(PILL_RECIPE);
        assertThat(workshop.audited).isEmpty();
    }

    @Test
    void forgeShouldRollBackAndReportNoQuality_whenTheBagIsFull() {
        Workshop full = stocked();
        full.freeSlots = 0;

        CraftItemUseCase.ForgeResult result = useCase(full).forge(CHARACTER, 20, "iron_sword");

        assertThat(result.outcome()).isEqualTo(CraftItemUseCase.Outcome.BAG_FULL);
        assertThat(result.quality()).isEmpty();
        assertThat(full.bag).containsEntry(ORE, 3).doesNotContainKey(SWORD);
        assertThat(full.gold).isEqualTo(100L);
    }

    @Test
    void shouldConsumeMaterialsAndGold_andGrantTheOutput() {
        Workshop workshop = stocked();

        assertThat(useCase(workshop).craft(CHARACTER, 10, "healing_pill")).isEqualTo(CraftItemUseCase.Outcome.OK);

        assertThat(workshop.bag).containsEntry(HERB, 3).containsEntry(CORE, 0).containsEntry(PILL, 3);
        assertThat(workshop.gold).isEqualTo(50L);
        assertThat(workshop.audited).containsExactly("CRAFT_ITEM");
    }

    @Test
    void shouldRefuseUnknownRecipesAndLowLevels_withoutOpeningATransaction() {
        Workshop workshop = stocked();

        assertThat(useCase(workshop).craft(CHARACTER, 10, "nope")).isEqualTo(CraftItemUseCase.Outcome.UNKNOWN_RECIPE);
        assertThat(useCase(workshop).craft(CHARACTER, 4, "healing_pill"))
                .isEqualTo(CraftItemUseCase.Outcome.LEVEL_TOO_LOW);
        assertThat(workshop.bag).containsEntry(HERB, 5);
    }

    @Test
    void shouldRollBackConsumedMaterials_whenALaterMaterialIsMissing() {
        Workshop workshop = stocked();
        workshop.bag.put(CORE, 0);

        assertThat(useCase(workshop).craft(CHARACTER, 10, "healing_pill"))
                .isEqualTo(CraftItemUseCase.Outcome.MISSING_MATERIALS);
        assertThat(workshop.bag).as("linh thảo đã tiêu phải quay lại").containsEntry(HERB, 5);
    }

    @Test
    void shouldRollBackEverything_whenGoldIsShort_orTheBagIsFull() {
        Workshop poor = stocked();
        poor.gold = 49L;
        assertThat(useCase(poor).craft(CHARACTER, 10, "healing_pill"))
                .isEqualTo(CraftItemUseCase.Outcome.INSUFFICIENT_GOLD);
        assertThat(poor.bag).containsEntry(HERB, 5).containsEntry(CORE, 1);

        Workshop full = stocked();
        full.freeSlots = 0;
        assertThat(useCase(full).craft(CHARACTER, 10, "healing_pill")).isEqualTo(CraftItemUseCase.Outcome.BAG_FULL);
        assertThat(full.bag).containsEntry(HERB, 5).doesNotContainKey(PILL);
        assertThat(full.gold).isEqualTo(100L);
        assertThat(full.audited).isEmpty();
    }
}
