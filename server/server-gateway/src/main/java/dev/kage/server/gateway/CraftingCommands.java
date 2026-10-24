package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.crafting.api.CraftingFacade;
import dev.kage.crafting.application.CraftItemUseCase;
import dev.kage.crafting.domain.Recipe;
import dev.kage.crafting.domain.RecipeMaterial;
import dev.kage.protocol.CraftItemCommand;
import dev.kage.protocol.CraftItemOutcome;
import dev.kage.protocol.CraftItemResponse;
import dev.kage.protocol.ListRecipesResponse;
import dev.kage.protocol.RecipeMaterialView;
import dev.kage.protocol.RecipeView;

/**
 * Hai lệnh lò đan (ADR-079) — tách khỏi {@code GatewayChannelHandler}, cùng khuôn {@link SectStorageCommands}.
 * Chạy trên Virtual Thread (chạm DB). Đứng gần trưởng lão và cấp nhân vật được kiểm Ở ĐÂY bằng dữ liệu server
 * (Điều 15).
 */
final class CraftingCommands {

    /** NPC giữ lò đan (D4). */
    static final String FURNACE_NPC_CODE = "town_elder";

    private static final Map<CraftItemUseCase.Outcome, CraftItemOutcome> OUTCOMES =
            new EnumMap<>(CraftItemUseCase.Outcome.class);

    static {
        OUTCOMES.put(CraftItemUseCase.Outcome.OK, CraftItemOutcome.CRAFT_ITEM_OUTCOME_OK);
        OUTCOMES.put(CraftItemUseCase.Outcome.UNKNOWN_RECIPE, CraftItemOutcome.CRAFT_ITEM_OUTCOME_UNKNOWN_RECIPE);
        OUTCOMES.put(CraftItemUseCase.Outcome.LEVEL_TOO_LOW, CraftItemOutcome.CRAFT_ITEM_OUTCOME_LEVEL_TOO_LOW);
        OUTCOMES.put(CraftItemUseCase.Outcome.MISSING_MATERIALS, CraftItemOutcome.CRAFT_ITEM_OUTCOME_MISSING_MATERIALS);
        OUTCOMES.put(CraftItemUseCase.Outcome.INSUFFICIENT_GOLD, CraftItemOutcome.CRAFT_ITEM_OUTCOME_INSUFFICIENT_GOLD);
        OUTCOMES.put(CraftItemUseCase.Outcome.BAG_FULL, CraftItemOutcome.CRAFT_ITEM_OUTCOME_BAG_FULL);
    }

    private final CraftingFacade crafting;
    private final CharacterFacade characters;
    private final NpcProximity proximity;

    CraftingCommands(CraftingFacade crafting, CharacterFacade characters, NpcProximity proximity) {
        this.crafting = crafting;
        this.characters = characters;
        this.proximity = proximity;
    }

    ListRecipesResponse list() {
        ListRecipesResponse.Builder response = ListRecipesResponse.newBuilder();
        for (Recipe recipe : crafting.recipes()) {
            RecipeView.Builder view = RecipeView.newBuilder()
                    .setCode(recipe.code())
                    .setOutputItemTemplateId(recipe.outputItemTemplateId())
                    .setOutputQuantity(recipe.outputQuantity())
                    .setGoldCost(recipe.goldCost())
                    .setRequiredLevel(recipe.requiredLevel());
            for (RecipeMaterial material : recipe.materials()) {
                view.addMaterials(RecipeMaterialView.newBuilder()
                        .setItemTemplateId(material.itemTemplateId())
                        .setQuantity(material.quantity()));
            }
            response.addRecipes(view);
        }
        return response.build();
    }

    CraftItemResponse craft(long characterId, CraftItemCommand command) {
        if (!proximity.isWithinReach(characterId, FURNACE_NPC_CODE)) {
            return CraftItemResponse.newBuilder()
                    .setOutcome(CraftItemOutcome.CRAFT_ITEM_OUTCOME_TOO_FAR)
                    .build();
        }
        int level = characters.findById(characterId).map(Character::level).orElse(1);
        CraftItemUseCase.Outcome outcome = crafting.craft(characterId, level, command.getRecipeCode());
        return CraftItemResponse.newBuilder().setOutcome(OUTCOMES.get(outcome)).build();
    }
}
