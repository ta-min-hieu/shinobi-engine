package dev.kage.server.gateway;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongConsumer;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.api.LuckyBagFacade;
import dev.kage.inventory.application.UseItemUseCase;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.inventory.domain.ItemUseEffect;
import dev.kage.inventory.domain.LuckyBagPort;
import dev.kage.protocol.LuckyBagRewardView;
import dev.kage.protocol.OpenLuckyBagOutcome;
import dev.kage.protocol.OpenLuckyBagsCommand;
import dev.kage.protocol.OpenLuckyBagsResponse;
import dev.kage.protocol.UseItemsCommand;
import dev.kage.protocol.UseItemsResponse;

/**
 * Dùng nhiều vật phẩm một lần (ADR-176) — lặp ĐÚNG đường dùng một món ({@link InventoryFacade#useItem}) và mở một túi
 * phúc ({@link LuckyBagFacade#open}), dừng ở lần đầu bị từ chối. Mỗi lần lặp là một transaction + audit riêng như
 * bấm tay, nên dừng giữa chừng không để lại trạng thái dở.
 *
 * <p>Chỉ món có hiệu ứng {@link #BULK_EFFECTS}: đan hồi máu có hồi chiêu chiến đấu 10 giây và tiến hoá linh thú chỉ
 * dùng được một lần, nên dùng nhiều không có nghĩa (và không được lách hồi chiêu).
 */
final class BulkUseCommands {

    /** Trần số lần một lệnh (server kẹp, client gửi bao nhiêu cũng vậy). */
    static final int MAX_COUNT = 50;

    static final Set<ItemUseEffect> BULK_EFFECTS = Set.of(ItemUseEffect.DOUBLE_EXP, ItemUseEffect.EXPAND_BAG);

    /** Việc chỉ gateway handler làm được: khoá i18n của một lần từ chối, và báo HUD hạn đan x2 (ADR-164). */
    record Hooks(Function<UseItemUseCase.Outcome, String> errorKey, LongConsumer onExpBuffChanged) {}

    private final InventoryFacade inventory;
    private final LuckyBagFacade luckyBags;
    private final LuckyBagCommands bagViews;

    BulkUseCommands(InventoryFacade inventory, LuckyBagFacade luckyBags) {
        this.inventory = inventory;
        this.luckyBags = luckyBags;
        this.bagViews = new LuckyBagCommands(luckyBags);
    }

    static int clamp(int count) {
        return Math.max(1, Math.min(MAX_COUNT, count));
    }

    /** Virtual Thread; {@code characterId} từ session. */
    UseItemsResponse useMany(long characterId, UseItemsCommand command, Hooks hooks) {
        Optional<ItemCatalogEntry> entry = entryOf(characterId, command.getInventoryItemId());
        if (entry.isEmpty()) {
            return UseItemsResponse.newBuilder()
                    .setStopErrorKey(hooks.errorKey().apply(UseItemUseCase.Outcome.ITEM_NOT_FOUND))
                    .build();
        }
        ItemUseEffect effect = entry.get().useEffect();
        if (effect == null || !BULK_EFFECTS.contains(effect)) {
            return UseItemsResponse.newBuilder()
                    .setStopErrorKey("error.item_not_bulk_usable")
                    .build();
        }
        UseItemsResponse.Builder response = UseItemsResponse.newBuilder();
        int used = 0;
        int remaining = 0;
        for (int i = 0; i < clamp(command.getCount()); i++) {
            UseItemUseCase.UseItemResult result = inventory.useItem(characterId, command.getInventoryItemId());
            if (result.outcome() != UseItemUseCase.Outcome.OK) {
                response.setStopErrorKey(hooks.errorKey().apply(result.outcome()));
                break;
            }
            used++;
            remaining = result.remainingQuantity();
            if (remaining == 0) {
                break;
            }
        }
        if (used > 0 && effect == ItemUseEffect.DOUBLE_EXP) {
            hooks.onExpBuffChanged().accept(characterId);
        }
        return response.setUsed(used).setRemainingQuantity(remaining).build();
    }

    /** Virtual Thread; {@code characterId} từ session. */
    OpenLuckyBagsResponse openMany(long characterId, OpenLuckyBagsCommand command) {
        long gold = 0;
        Map<Long, Integer> items = new LinkedHashMap<>();
        int opened = 0;
        LuckyBagPort.Outcome stop = LuckyBagPort.Outcome.OK;
        for (int i = 0; i < clamp(command.getCount()); i++) {
            LuckyBagPort.Opened result = luckyBags.open(characterId, command.getBagItemTemplateId());
            if (result.outcome() != LuckyBagPort.Outcome.OK) {
                stop = result.outcome();
                break;
            }
            opened++;
            gold += result.reward().gold();
            if (result.reward().itemTemplateId() != 0) {
                items.merge(result.reward().itemTemplateId(), result.reward().quantity(), Integer::sum);
            }
        }
        OpenLuckyBagsResponse.Builder response = OpenLuckyBagsResponse.newBuilder()
                .setOpened(opened)
                .setStopOutcome(OpenLuckyBagOutcome.valueOf("OPEN_LUCKY_BAG_OUTCOME_" + stop.name()))
                .setBags(bagViews.bags(characterId));
        if (gold > 0) {
            response.addRewards(LuckyBagRewardView.newBuilder().setGold(gold));
        }
        items.forEach((template, quantity) -> response.addRewards(
                LuckyBagRewardView.newBuilder().setItemTemplateId(template).setQuantity(quantity)));
        return response.build();
    }

    private Optional<ItemCatalogEntry> entryOf(long characterId, long inventoryItemId) {
        Optional<Long> template = inventory
                .listInventory(characterId)
                .inventory()
                .findItem(inventoryItemId)
                .map(ItemStack::itemTemplateId);
        return template.flatMap(id -> inventory.itemCatalog().entries().stream()
                .filter(entry -> entry.id() == id)
                .findFirst());
    }
}
