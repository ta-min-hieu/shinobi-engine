package dev.kage.crafting.api;

import java.util.List;

import dev.kage.crafting.application.CraftItemUseCase;
import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeKind;

/** API của {@code feature-crafting} (ADR-079). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class CraftingFacade {

    /** Composition không có lò đan (test) — không công thức nào. */
    public static final CraftingFacade DISABLED =
            new CraftingFacade(new CraftItemUseCase(List::of, null, action -> action.accept(null)));

    private final CraftItemUseCase useCase;

    /** Dựng ở composition root. */
    public CraftingFacade(CraftItemUseCase useCase) {
        this.useCase = useCase;
    }

    /** Mọi công thức luyện đan, theo thứ tự hiển thị. */
    public List<Recipe> recipes() {
        return useCase.recipes(RecipeKind.ALCHEMY);
    }

    /** Mọi công thức luyện khí (ADR-120), theo thứ tự hiển thị. */
    public List<Recipe> forgeRecipes() {
        return useCase.recipes(RecipeKind.FORGE);
    }

    /** Luyện một mẻ — {@code level} đọc từ DB ở chỗ gọi, không từ gói. */
    public CraftItemUseCase.Outcome craft(long characterId, int level, String recipeCode) {
        return useCase.craft(characterId, level, recipeCode);
    }

    /** Luyện khí một món — {@code level} đọc từ DB ở chỗ gọi; server tung phẩm chất. */
    public CraftItemUseCase.ForgeResult forge(long characterId, int level, String recipeCode) {
        return useCase.forge(characterId, level, recipeCode);
    }
}
