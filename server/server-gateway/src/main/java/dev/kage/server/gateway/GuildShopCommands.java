package dev.kage.server.gateway;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import dev.kage.guild.api.GuildShopFacade;
import dev.kage.guild.domain.GuildShopItem;
import dev.kage.guild.domain.GuildShopOutcome;
import dev.kage.guild.domain.GuildShopWallet;
import dev.kage.protocol.BuyGuildShopItemCommand;
import dev.kage.protocol.BuyGuildShopItemOutcome;
import dev.kage.protocol.BuyGuildShopItemResponse;
import dev.kage.protocol.GuildShopItemView;
import dev.kage.protocol.ListGuildShopResponse;

/** Hai lệnh cửa hàng bang (ADR-080) — tách khỏi {@code GatewayChannelHandler}. Chạy trên Virtual Thread (chạm DB). */
final class GuildShopCommands {

    private static final Map<GuildShopOutcome, BuyGuildShopItemOutcome> OUTCOMES =
            new EnumMap<>(GuildShopOutcome.class);

    static {
        OUTCOMES.put(GuildShopOutcome.OK, BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_OK);
        OUTCOMES.put(GuildShopOutcome.NOT_IN_GUILD, BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_NOT_IN_GUILD);
        OUTCOMES.put(GuildShopOutcome.UNKNOWN_ITEM, BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_UNKNOWN_ITEM);
        OUTCOMES.put(
                GuildShopOutcome.GUILD_LEVEL_TOO_LOW,
                BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_GUILD_LEVEL_TOO_LOW);
        OUTCOMES.put(
                GuildShopOutcome.NOT_ENOUGH_CONTRIBUTION,
                BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_NOT_ENOUGH_CONTRIBUTION);
        OUTCOMES.put(GuildShopOutcome.BAG_FULL, BuyGuildShopItemOutcome.BUY_GUILD_SHOP_ITEM_OUTCOME_BAG_FULL);
    }

    private final GuildShopFacade shop;

    GuildShopCommands(GuildShopFacade shop) {
        this.shop = shop;
    }

    ListGuildShopResponse list(long characterId) {
        ListGuildShopResponse.Builder response = ListGuildShopResponse.newBuilder();
        for (GuildShopItem item : shop.items()) {
            response.addItems(GuildShopItemView.newBuilder()
                    .setItemTemplateId(item.itemTemplateId())
                    .setContributionCost(item.contributionCost())
                    .setRequiredGuildLevel(item.requiredGuildLevel()));
        }
        Optional<GuildShopWallet> wallet = shop.walletOf(characterId);
        wallet.ifPresent(found -> response.setInGuild(true)
                .setGuildLevel(found.guildLevel())
                .setContributionAvailable(found.available()));
        return response.build();
    }

    BuyGuildShopItemResponse buy(long characterId, BuyGuildShopItemCommand command) {
        GuildShopOutcome outcome = shop.buy(characterId, command.getItemTemplateId());
        return BuyGuildShopItemResponse.newBuilder()
                .setOutcome(OUTCOMES.get(outcome))
                .setShop(list(characterId))
                .build();
    }
}
