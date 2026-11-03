package dev.kage.server.gateway;

import dev.kage.inventory.api.LuckyBagFacade;
import dev.kage.inventory.domain.LuckyBagPort;
import dev.kage.protocol.GetLuckyBagsResponse;
import dev.kage.protocol.LuckyBagRewardView;
import dev.kage.protocol.LuckyBagView;
import dev.kage.protocol.OpenLuckyBagCommand;
import dev.kage.protocol.OpenLuckyBagOutcome;
import dev.kage.protocol.OpenLuckyBagResponse;

/** Túi phúc (ADR-163) — bảng quà công khai + mở một túi; server rút và cấp quà. */
final class LuckyBagCommands {

    private final LuckyBagFacade facade;

    LuckyBagCommands(LuckyBagFacade facade) {
        this.facade = facade;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetLuckyBagsResponse bags(long characterId) {
        GetLuckyBagsResponse.Builder response = GetLuckyBagsResponse.newBuilder();
        for (LuckyBagPort.Bag bag : facade.bags(characterId)) {
            LuckyBagView.Builder view = LuckyBagView.newBuilder()
                    .setBagItemTemplateId(bag.bagItemTemplateId())
                    .setOwned(bag.owned());
            bag.rewards().forEach(reward -> view.addRewards(view(reward)));
            response.addBags(view);
        }
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    OpenLuckyBagResponse open(long characterId, OpenLuckyBagCommand command) {
        LuckyBagPort.Opened opened = facade.open(characterId, command.getBagItemTemplateId());
        return OpenLuckyBagResponse.newBuilder()
                .setOutcome(wire(opened.outcome()))
                .setReward(view(opened.reward()))
                .setBags(bags(characterId))
                .build();
    }

    private static LuckyBagRewardView view(LuckyBagPort.Reward reward) {
        return LuckyBagRewardView.newBuilder()
                .setGold(reward.gold())
                .setItemTemplateId(reward.itemTemplateId())
                .setQuantity(reward.quantity())
                .setWeight(reward.weight())
                .build();
    }

    private static OpenLuckyBagOutcome wire(LuckyBagPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_OK;
            case NOT_A_BAG -> OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_NOT_A_BAG;
            case NO_BAG -> OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_NO_BAG;
            case BAG_FULL -> OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_BAG_FULL;
            case GOLD_FULL -> OpenLuckyBagOutcome.OPEN_LUCKY_BAG_OUTCOME_GOLD_FULL;
        };
    }
}
