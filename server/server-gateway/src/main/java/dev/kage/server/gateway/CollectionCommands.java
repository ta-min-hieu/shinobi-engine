package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongToIntFunction;

import dev.kage.achievement.domain.BestiaryPort;
import dev.kage.achievement.domain.ReputationPort;
import dev.kage.achievement.domain.ReputationRules;
import dev.kage.guild.domain.GuildAuctionPort;
import dev.kage.guild.domain.GuildAuctionRules;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.RefinementPolicy;
import dev.kage.inventory.domain.RefinementPort;
import dev.kage.protocol.BestiaryEntryView;
import dev.kage.protocol.BidGuildAuctionOutcome;
import dev.kage.protocol.BidGuildAuctionResponse;
import dev.kage.protocol.BuyReputationItemOutcome;
import dev.kage.protocol.BuyReputationItemResponse;
import dev.kage.protocol.GetBestiaryResponse;
import dev.kage.protocol.GetReputationResponse;
import dev.kage.protocol.GuildAuctionLotView;
import dev.kage.protocol.ListGuildAuctionsResponse;
import dev.kage.protocol.ListRefinementsResponse;
import dev.kage.protocol.RefineItemOutcome;
import dev.kage.protocol.RefineItemResponse;
import dev.kage.protocol.RefinedItemView;
import dev.kage.protocol.RefinementLineView;
import dev.kage.protocol.ReputationShopItemView;

/** Sổ sưu tầm (ADR-108), danh vọng (ADR-109), tẩy luyện (ADR-111), đấu giá bang (ADR-112) cho MỘT
 * kết nối. Chạy trên Virtual Thread (chạm DB). */
final class CollectionCommands {

    private final CollectionFacades facades;
    private final InventoryFacade inventory;

    CollectionCommands(CollectionFacades facades, InventoryFacade inventory) {
        this.facades = facades;
        this.inventory = inventory;
    }

    GetBestiaryResponse bestiary(long characterId) {
        GetBestiaryResponse.Builder response = GetBestiaryResponse.newBuilder();
        for (BestiaryPort.Progress progress : facades.bestiary().progressOf(characterId)) {
            BestiaryPort.Entry entry = progress.entry();
            response.addEntries(BestiaryEntryView.newBuilder()
                    .setMonsterCode(entry.monsterCode())
                    .setLoreKey(entry.loreKey())
                    .setKills(progress.kills())
                    .setKillsRequired(entry.killsRequired())
                    .setUnlocked(progress.unlocked())
                    .setBonusStat(entry.bonusStat().name())
                    .setBonusValue(entry.bonusValue()));
        }
        return response.build();
    }

    GetReputationResponse reputation(long characterId) {
        ReputationPort.Standing standing = facades.reputation().standingOf(characterId);
        Map<Long, String> codes = new HashMap<>();
        for (ItemCatalogEntry entry : inventory.itemCatalog().entries()) {
            codes.put(entry.id(), entry.code());
        }
        GetReputationResponse.Builder response = GetReputationResponse.newBuilder()
                .setEarned(standing.earned())
                .setAvailable(standing.available())
                .setTier(ReputationRules.tierOf(standing.earned()))
                .setNextTierAt(ReputationRules.nextTierAt(standing.earned()).orElse(0L));
        for (ReputationPort.ShopItem item : facades.reputation().shop()) {
            response.addItems(ReputationShopItemView.newBuilder()
                    .setItemTemplateId(item.itemTemplateId())
                    .setItemCode(codes.getOrDefault(item.itemTemplateId(), ""))
                    .setPrice(item.price())
                    .setRequiredTier(item.requiredTier()));
        }
        return response.build();
    }

    BuyReputationItemResponse buy(long characterId, long itemTemplateId) {
        ReputationPort.BuyOutcome outcome = facades.reputation().buy(characterId, itemTemplateId);
        return BuyReputationItemResponse.newBuilder()
                .setOutcome(BuyReputationItemOutcome.valueOf("BUY_REPUTATION_ITEM_OUTCOME_" + outcome.name()))
                .setStatus(reputation(characterId))
                .build();
    }

    ListRefinementsResponse refinements(long characterId) {
        ListRefinementsResponse.Builder response = ListRefinementsResponse.newBuilder()
                .setStones(stonesOf(characterId))
                .setStoneCost(RefinementPolicy.STONE_COST)
                .setLockedCost(RefinementPolicy.LOCKED_STONE_COST);
        for (RefinementPort.WornItem item : facades.refinement().wornItems(characterId)) {
            RefinedItemView.Builder view = RefinedItemView.newBuilder()
                    .setItemId(item.itemId())
                    .setItemTemplateId(item.itemTemplateId())
                    .setSlot(item.slot());
            for (RefinementPolicy.Line line : item.lines()) {
                view.addLines(RefinementLineView.newBuilder()
                        .setIndex(line.index())
                        .setStat(line.stat().name())
                        .setValue(line.value()));
            }
            response.addItems(view);
        }
        return response.build();
    }

    /** Tẩy; {@code maxHp} là máu tối đa sau khi áp lại chỉ số (0 nếu từ chối). */
    RefineItemResponse refine(long characterId, long itemId, int lockedLine, LongToIntFunction reapply) {
        RefinementPort.Result result = facades.refinement().refine(characterId, itemId, lockedLine);
        int maxHp = result.outcome() == RefinementPort.Outcome.OK ? reapply.applyAsInt(characterId) : 0;
        return RefineItemResponse.newBuilder()
                .setOutcome(RefineItemOutcome.valueOf(
                        "REFINE_ITEM_OUTCOME_" + result.outcome().name()))
                .setStatus(refinements(characterId))
                .setMaxHp(maxHp)
                .build();
    }

    private int stonesOf(long characterId) {
        long stoneId = inventory.itemCatalog().entries().stream()
                .filter(entry -> entry.code().equals(RefinementPolicy.STONE_CODE))
                .mapToLong(ItemCatalogEntry::id)
                .findFirst()
                .orElse(0L);
        return inventory.listInventory(characterId).inventory().items().stream()
                .filter(stack -> stack.itemTemplateId() == stoneId)
                .mapToInt(ItemStack::quantity)
                .sum();
    }

    ListGuildAuctionsResponse guildAuctions(long characterId) {
        Instant now = Instant.now();
        ListGuildAuctionsResponse.Builder response = ListGuildAuctionsResponse.newBuilder();
        for (GuildAuctionPort.Lot lot : facades.guildAuctions().openLotsOf(characterId, now)) {
            response.addLots(GuildAuctionLotView.newBuilder()
                    .setLotId(lot.id())
                    .setItemTemplateId(lot.itemTemplateId())
                    .setQuantity(lot.quantity())
                    .setCurrentBid(lot.currentBid())
                    .setLeaderName(lot.leaderName())
                    .setLeadingIsMe(lot.leadingIsMe())
                    .setSecondsLeft(
                            Math.max(0L, Duration.between(now, lot.endsAt()).toSeconds()))
                    .setMinimumBid(GuildAuctionRules.minimumBid(lot.currentBid()))
                    .setParticipants(lot.participants()));
        }
        return response.build();
    }

    BidGuildAuctionResponse bidGuildAuction(long characterId, long lotId, long amount) {
        GuildAuctionPort.BidOutcome outcome = facades.guildAuctions().bid(characterId, lotId, amount, Instant.now());
        return BidGuildAuctionResponse.newBuilder()
                .setOutcome(BidGuildAuctionOutcome.valueOf("BID_GUILD_AUCTION_OUTCOME_" + outcome.name()))
                .setStatus(guildAuctions(characterId))
                .build();
    }
}
