package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.protocol.BuyHonorItemCommand;
import dev.kage.protocol.BuyHonorItemOutcome;
import dev.kage.protocol.BuyHonorItemResponse;
import dev.kage.protocol.GetHonorShopResponse;
import dev.kage.protocol.HonorShopItemView;
import dev.kage.pvp.api.HonorFacade;
import dev.kage.pvp.domain.HonorPort;

/** Cửa hàng điểm vinh dự (ADR-155) — xem cửa hàng, đổi một lần; server kiểm điểm, trần tuần, túi. */
final class HonorCommands {

    private final HonorFacade facade;
    private final Clock clock;

    HonorCommands(HonorFacade facade, Clock clock) {
        this.facade = facade;
        this.clock = clock;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    GetHonorShopResponse shop(long characterId) {
        HonorPort.Shop shop = facade.shop(characterId, clock.instant());
        GetHonorShopResponse.Builder response =
                GetHonorShopResponse.newBuilder().setAvailable(shop.available());
        for (HonorPort.ShopItem item : shop.items()) {
            response.addItems(HonorShopItemView.newBuilder()
                    .setItemTemplateId(item.itemTemplateId())
                    .setPrice(item.price())
                    .setQuantity(item.quantity())
                    .setWeeklyLimit(item.weeklyLimit())
                    .setBoughtThisWeek(item.boughtThisWeek()));
        }
        return response.build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    BuyHonorItemResponse buy(long characterId, BuyHonorItemCommand command) {
        HonorPort.Outcome outcome = facade.buy(characterId, command.getItemTemplateId(), clock.instant());
        return BuyHonorItemResponse.newBuilder()
                .setOutcome(wire(outcome))
                .setShop(shop(characterId))
                .build();
    }

    private static BuyHonorItemOutcome wire(HonorPort.Outcome outcome) {
        return switch (outcome) {
            case OK -> BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_OK;
            case UNKNOWN_ITEM -> BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_UNKNOWN_ITEM;
            case NOT_ENOUGH_HONOR -> BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_NOT_ENOUGH_HONOR;
            case WEEKLY_LIMIT -> BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_WEEKLY_LIMIT;
            case BAG_FULL -> BuyHonorItemOutcome.BUY_HONOR_ITEM_OUTCOME_BAG_FULL;
        };
    }
}
