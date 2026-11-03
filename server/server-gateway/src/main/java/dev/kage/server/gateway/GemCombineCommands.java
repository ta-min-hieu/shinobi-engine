package dev.kage.server.gateway;

import dev.kage.inventory.api.GemCombineFacade;
import dev.kage.inventory.domain.GemCombinePort;
import dev.kage.protocol.CombineGemsCommand;
import dev.kage.protocol.CombineGemsOutcome;
import dev.kage.protocol.CombineGemsResponse;
import dev.kage.protocol.GemRecipeView;
import dev.kage.protocol.GetGemRecipesResponse;

/** Ghép Linh Ngọc (ADR-161) — bảng công thức + ghép một lần; server kiểm ngọc, vàng, túi. */
final class GemCombineCommands {

    private final GemCombineFacade facade;

    GemCombineCommands(GemCombineFacade facade) {
        this.facade = facade;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetGemRecipesResponse recipes(long characterId) {
        GetGemRecipesResponse.Builder response = GetGemRecipesResponse.newBuilder();
        for (GemCombinePort.Recipe recipe : facade.recipes(characterId)) {
            response.addRecipes(GemRecipeView.newBuilder()
                    .setSourceItemTemplateId(recipe.sourceItemTemplateId())
                    .setResultItemTemplateId(recipe.resultItemTemplateId())
                    .setSourceQuantity(recipe.sourceQuantity())
                    .setGoldFee(recipe.goldFee())
                    .setOwned(recipe.owned()));
        }
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    CombineGemsResponse combine(long characterId, CombineGemsCommand command) {
        GemCombinePort.Outcome outcome = facade.combine(characterId, command.getSourceItemTemplateId());
        CombineGemsResponse.Builder response =
                CombineGemsResponse.newBuilder().setOutcome(wire(outcome)).setRecipes(recipes(characterId));
        if (outcome == GemCombinePort.Outcome.OK) {
            facade.recipes(characterId).stream()
                    .filter(r -> r.sourceItemTemplateId() == command.getSourceItemTemplateId())
                    .findFirst()
                    .ifPresent(r -> response.setResultItemTemplateId(r.resultItemTemplateId()));
        }
        return response.build();
    }

    private static CombineGemsOutcome wire(GemCombinePort.Outcome outcome) {
        return switch (outcome) {
            case OK -> CombineGemsOutcome.COMBINE_GEMS_OUTCOME_OK;
            case UNKNOWN_RECIPE -> CombineGemsOutcome.COMBINE_GEMS_OUTCOME_UNKNOWN_RECIPE;
            case NOT_ENOUGH_GEMS -> CombineGemsOutcome.COMBINE_GEMS_OUTCOME_NOT_ENOUGH_GEMS;
            case NOT_ENOUGH_GOLD -> CombineGemsOutcome.COMBINE_GEMS_OUTCOME_NOT_ENOUGH_GOLD;
            case BAG_FULL -> CombineGemsOutcome.COMBINE_GEMS_OUTCOME_BAG_FULL;
        };
    }
}
