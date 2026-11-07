package dev.kage.server.gateway;

import java.util.Map;
import java.util.Optional;

import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.EnhancementTable;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.inventory.domain.ItemStack;
import dev.kage.protocol.LinkedItemView;

/**
 * Khoe vật phẩm vào chat (ADR-188) — đọc món THẬT trong túi người gửi lúc gửi (cường hoá, ngọc, chỉ số hiệu dụng do
 * server tính) để gắn vào tin chat. Client chỉ gửi id món của chính mình; không khai được chỉ số.
 */
final class ItemLinkCommands {

    private final InventoryFacade inventory;

    ItemLinkCommands(InventoryFacade inventory) {
        this.inventory = inventory;
    }

    /** Virtual Thread; món {@code itemId} của {@code characterId}, rỗng nếu không có trong túi / trên người. */
    Optional<LinkedItemView> resolve(long characterId, long itemId) {
        Optional<ItemStack> item =
                inventory.listInventory(characterId).inventory().findItem(itemId);
        if (item.isEmpty()) {
            return Optional.empty();
        }
        ItemStack stack = item.get();
        Map<Long, Long> sockets = inventory.socketsOf(characterId);
        LinkedItemView.Builder view = LinkedItemView.newBuilder()
                .setItemTemplateId(stack.itemTemplateId())
                .setEnhanceLevel(stack.enhanceLevel())
                .setSocketGemTemplateId(sockets.getOrDefault(stack.id(), 0L))
                .setQuantity(stack.quantity());
        inventory.itemCatalog().entries().stream()
                .filter(entry -> entry.id() == stack.itemTemplateId())
                .findFirst()
                .ifPresent(entry -> withStats(view, entry, stack.enhanceLevel()));
        return Optional.of(view.build());
    }

    private static void withStats(LinkedItemView.Builder view, ItemCatalogEntry entry, int enhanceLevel) {
        view.setBonusAtk(EnhancementTable.enhancedBonus(entry.bonusAtk(), enhanceLevel))
                .setBonusDef(EnhancementTable.enhancedBonus(entry.bonusDef(), enhanceLevel))
                .setBonusRes(EnhancementTable.enhancedBonus(entry.bonusRes(), enhanceLevel))
                .setBonusHp(EnhancementTable.enhancedBonus(entry.bonusHp(), enhanceLevel));
    }
}
