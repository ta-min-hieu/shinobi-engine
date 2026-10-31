package dev.kage.crafting.application;

import java.io.Serial;
import java.util.List;
import java.util.Optional;
import java.util.SplittableRandom;

import dev.kage.crafting.domain.CraftingWorkshopPort;
import dev.kage.crafting.domain.ForgeQuality;
import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeKind;
import dev.kage.crafting.domain.RecipeMaterial;
import dev.kage.crafting.domain.RecipePort;
import dev.kage.crafting.domain.Transaction;
import dev.kage.crafting.domain.TransactionRunner;

/**
 * Luyện một mẻ theo công thức (ADR-079 D3): khoá túi đồ → tiêu nguyên liệu → trừ linh thạch → cấp món ra → audit, tất
 * cả trong MỘT transaction. Nhánh từ chối giữa chừng ném để rollback trọn — "return sớm" sau khi đã trừ sẽ commit phần
 * đã trừ (cùng bẫy {@code SendMailUseCase} đã chú thích). {@code characterId} và cấp đều từ server, không từ gói.
 * Luyện khí (ADR-120) đi cùng đường, chỉ khác bước cấp: tung {@link ForgeQuality} rồi cấp món kèm dòng tẩy luyện.
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

    /**
     * Kết quả một lần luyện khí.
     *
     * @param quality có khi {@code outcome == OK}
     */
    public record ForgeResult(Outcome outcome, Optional<ForgeQuality> quality) {}

    private final RecipePort recipes;
    private final CraftingWorkshopPort workshop;
    private final TransactionRunner tx;
    private final SplittableRandom random = new SplittableRandom();

    public CraftItemUseCase(RecipePort recipes, CraftingWorkshopPort workshop, TransactionRunner tx) {
        this.recipes = recipes;
        this.workshop = workshop;
        this.tx = tx;
    }

    /** Mọi công thức một loại, theo thứ tự hiển thị. */
    public List<Recipe> recipes(RecipeKind kind) {
        return recipes.recipes().stream()
                .filter(recipe -> recipe.kind() == kind)
                .toList();
    }

    /** Luyện đan — mã của công thức luyện khí bị coi là không tồn tại (luyện khí phải đi qua {@link #forge}). */
    public Outcome craft(long characterId, int level, String recipeCode) {
        Optional<Recipe> recipe = find(RecipeKind.ALCHEMY, recipeCode);
        if (recipe.isEmpty()) {
            return Outcome.UNKNOWN_RECIPE;
        }
        return run(characterId, level, recipe.get(), ForgeQuality.COMMON);
    }

    /** Luyện khí — server tung phẩm chất TRƯỚC transaction; mọi nhánh từ chối không tốn gì. */
    public ForgeResult forge(long characterId, int level, String recipeCode) {
        Optional<Recipe> recipe = find(RecipeKind.FORGE, recipeCode);
        if (recipe.isEmpty()) {
            return new ForgeResult(Outcome.UNKNOWN_RECIPE, Optional.empty());
        }
        ForgeQuality quality;
        synchronized (random) {
            quality = ForgeQuality.roll(random);
        }
        Outcome outcome = run(characterId, level, recipe.get(), quality);
        return new ForgeResult(outcome, outcome == Outcome.OK ? Optional.of(quality) : Optional.empty());
    }

    private Optional<Recipe> find(RecipeKind kind, String recipeCode) {
        return recipes.recipes().stream()
                .filter(candidate ->
                        candidate.kind() == kind && candidate.code().equals(recipeCode))
                .findFirst();
    }

    private Outcome run(long characterId, int level, Recipe recipe, ForgeQuality quality) {
        if (level < recipe.requiredLevel()) {
            return Outcome.LEVEL_TOO_LOW;
        }
        try {
            tx.inTransaction(t -> craftIn(t, characterId, recipe, quality));
        } catch (Abort abort) {
            return abort.outcome;
        }
        return Outcome.OK;
    }

    private void craftIn(Transaction t, long characterId, Recipe recipe, ForgeQuality quality) {
        workshop.lockInventory(t, characterId);
        for (RecipeMaterial material : recipe.materials()) {
            if (!workshop.consumeMaterial(t, characterId, material.itemTemplateId(), material.quantity())) {
                throw new Abort(Outcome.MISSING_MATERIALS);
            }
        }
        if (recipe.goldCost() > 0 && !workshop.deductGold(t, characterId, recipe.goldCost())) {
            throw new Abort(Outcome.INSUFFICIENT_GOLD);
        }
        grant(t, characterId, recipe, quality);
    }

    /** Cấp món ra + audit — luyện khí kèm dòng tẩy luyện theo phẩm chất. */
    private void grant(Transaction t, long characterId, Recipe recipe, ForgeQuality quality) {
        boolean forge = recipe.kind() == RecipeKind.FORGE;
        boolean granted = forge
                ? workshop.grantForged(t, characterId, recipe.outputItemTemplateId(), quality.refinementLines())
                : workshop.grantItem(t, characterId, recipe.outputItemTemplateId(), recipe.outputQuantity());
        if (!granted) {
            throw new Abort(Outcome.BAG_FULL);
        }
        workshop.audit(t, characterId, forge ? "FORGE_ITEM" : "CRAFT_ITEM", detail(recipe, quality));
    }

    private static String detail(Recipe recipe, ForgeQuality quality) {
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
                + ",\"outputQuantity\":" + recipe.outputQuantity() + ",\"quality\":\"" + quality.name()
                + "\",\"materials\":" + materials + "}";
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
