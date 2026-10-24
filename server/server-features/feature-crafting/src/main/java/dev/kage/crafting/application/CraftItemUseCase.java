package dev.kage.crafting.application;

import java.io.Serial;
import java.util.List;
import java.util.Optional;

import dev.kage.crafting.domain.CraftingWorkshopPort;
import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeMaterial;
import dev.kage.crafting.domain.RecipePort;
import dev.kage.crafting.domain.Transaction;
import dev.kage.crafting.domain.TransactionRunner;

/**
 * Luyện một mẻ theo công thức (ADR-079 D3): khoá túi đồ → tiêu nguyên liệu → trừ linh thạch → cấp món ra → audit, tất
 * cả trong MỘT transaction. Nhánh từ chối giữa chừng ném để rollback trọn — "return sớm" sau khi đã trừ sẽ commit phần
 * đã trừ (cùng bẫy {@code SendMailUseCase} đã chú thích). {@code characterId} và cấp đều từ server, không từ gói.
 */
public final class CraftItemUseCase {

    /** Kết quả một lần luyện. */
    public enum Outcome {
        OK,
        UNKNOWN_RECIPE,
        LEVEL_TOO_LOW,
        MISSING_MATERIALS,
        INSUFFICIENT_GOLD,
        BAG_FULL
    }

    private final RecipePort recipes;
    private final CraftingWorkshopPort workshop;
    private final TransactionRunner tx;

    public CraftItemUseCase(RecipePort recipes, CraftingWorkshopPort workshop, TransactionRunner tx) {
        this.recipes = recipes;
        this.workshop = workshop;
        this.tx = tx;
    }

    /** Mọi công thức, theo thứ tự hiển thị. */
    public List<Recipe> recipes() {
        return recipes.recipes();
    }

    public Outcome craft(long characterId, int level, String recipeCode) {
        Optional<Recipe> recipe = recipes.recipes().stream()
                .filter(candidate -> candidate.code().equals(recipeCode))
                .findFirst();
        if (recipe.isEmpty()) {
            return Outcome.UNKNOWN_RECIPE;
        }
        if (level < recipe.get().requiredLevel()) {
            return Outcome.LEVEL_TOO_LOW;
        }
        try {
            tx.inTransaction(t -> craftIn(t, characterId, recipe.get()));
        } catch (Abort abort) {
            return abort.outcome;
        }
        return Outcome.OK;
    }

    private void craftIn(Transaction t, long characterId, Recipe recipe) {
        workshop.lockInventory(t, characterId);
        for (RecipeMaterial material : recipe.materials()) {
            if (!workshop.consumeMaterial(t, characterId, material.itemTemplateId(), material.quantity())) {
                throw new Abort(Outcome.MISSING_MATERIALS);
            }
        }
        if (recipe.goldCost() > 0 && !workshop.deductGold(t, characterId, recipe.goldCost())) {
            throw new Abort(Outcome.INSUFFICIENT_GOLD);
        }
        if (!workshop.grantItem(t, characterId, recipe.outputItemTemplateId(), recipe.outputQuantity())) {
            throw new Abort(Outcome.BAG_FULL);
        }
        workshop.audit(t, characterId, "CRAFT_ITEM", detail(recipe));
    }

    private static String detail(Recipe recipe) {
        StringBuilder materials = new StringBuilder("[");
        for (RecipeMaterial material : recipe.materials()) {
            if (materials.length() > 1) {
                materials.append(',');
            }
            materials
                    .append("{\"itemTemplateId\":")
                    .append(material.itemTemplateId())
                    .append(",\"quantity\":")
                    .append(material.quantity())
                    .append('}');
        }
        materials.append(']');
        return "{\"recipe\":\"" + recipe.code() + "\",\"goldCost\":" + recipe.goldCost()
                + ",\"outputItemTemplateId\":" + recipe.outputItemTemplateId()
                + ",\"outputQuantity\":" + recipe.outputQuantity() + ",\"materials\":" + materials + "}";
    }

    /** Từ chối giữa transaction — ném để rollback. */
    private static final class Abort extends RuntimeException {
        @Serial
        private static final long serialVersionUID = 1L;

        private final transient Outcome outcome;

        Abort(Outcome outcome) {
            super(outcome.name(), null, false, false);
            this.outcome = outcome;
        }
    }
}
