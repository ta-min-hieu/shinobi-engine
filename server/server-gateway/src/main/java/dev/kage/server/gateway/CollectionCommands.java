package dev.kage.server.gateway;

import java.util.HashMap;
import java.util.Map;

import dev.kage.achievement.domain.BestiaryPort;
import dev.kage.achievement.domain.ReputationPort;
import dev.kage.achievement.domain.ReputationRules;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.protocol.BestiaryEntryView;
import dev.kage.protocol.BuyReputationItemOutcome;
import dev.kage.protocol.BuyReputationItemResponse;
import dev.kage.protocol.GetBestiaryResponse;
import dev.kage.protocol.GetReputationResponse;
import dev.kage.protocol.ReputationShopItemView;

/** Sổ sưu tầm (ADR-108) và danh vọng (ADR-109) cho MỘT kết nối. Chạy trên Virtual Thread (chạm DB). */
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
}
