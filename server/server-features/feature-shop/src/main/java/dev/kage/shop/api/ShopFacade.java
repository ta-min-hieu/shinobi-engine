package dev.kage.shop.api;

import java.util.List;

import dev.kage.shop.application.BuyItemUseCase;
import dev.kage.shop.application.ListShopItemsUseCase;
import dev.kage.shop.application.SellItemUseCase;
import dev.kage.shop.domain.ShopListing;

/**
 * API công khai của {@code feature-shop} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code shop_item} trực tiếp (Điều 24).
 */
public final class ShopFacade {

    private final ListShopItemsUseCase listShopItemsUseCase;
    private final BuyItemUseCase buyItemUseCase;
    private final SellItemUseCase sellItemUseCase;

    public ShopFacade(
            ListShopItemsUseCase listShopItemsUseCase, BuyItemUseCase buyItemUseCase, SellItemUseCase sellItemUseCase) {
        this.listShopItemsUseCase = listShopItemsUseCase;
        this.buyItemUseCase = buyItemUseCase;
        this.sellItemUseCase = sellItemUseCase;
    }

    /** Toàn bộ danh mục shop (item bán được, giá mua/bán). */
    public List<ShopListing> listItems() {
        return listShopItemsUseCase.execute();
    }

    /**
     * Mua {@code quantity} item template {@code itemTemplateId} — trừ gold, cộng vào túi đồ.
     * {@code idempotencyKey} (ADR-025) — client sinh UUID, giữ nguyên khi tự retry.
     */
    public BuyResult buyItem(long characterId, long itemTemplateId, int quantity, String idempotencyKey) {
        return switch (buyItemUseCase.execute(characterId, itemTemplateId, quantity, idempotencyKey)) {
            case OK -> BuyResult.OK;
            case ITEM_NOT_IN_SHOP -> BuyResult.ITEM_NOT_IN_SHOP;
            case INSUFFICIENT_GOLD -> BuyResult.INSUFFICIENT_GOLD;
            case INVENTORY_FULL -> BuyResult.INVENTORY_FULL;
            case DUPLICATE_REQUEST -> BuyResult.DUPLICATE_REQUEST;
        };
    }

    /** Bán {@code quantity} của item {@code itemId} (ID thật trong túi đồ) — cộng gold. */
    public SellResult sellItem(long characterId, long itemId, int quantity, String idempotencyKey) {
        return switch (sellItemUseCase.execute(characterId, itemId, quantity, idempotencyKey)) {
            case OK -> SellResult.OK;
            case ITEM_NOT_FOUND -> SellResult.ITEM_NOT_FOUND;
            case ITEM_NOT_SELLABLE -> SellResult.ITEM_NOT_SELLABLE;
            case INSUFFICIENT_QUANTITY -> SellResult.INSUFFICIENT_QUANTITY;
            case DUPLICATE_REQUEST -> SellResult.DUPLICATE_REQUEST;
        };
    }

    public enum BuyResult {
        OK,
        ITEM_NOT_IN_SHOP,
        INSUFFICIENT_GOLD,
        INVENTORY_FULL,
        DUPLICATE_REQUEST
    }

    public enum SellResult {
        OK,
        ITEM_NOT_FOUND,
        ITEM_NOT_SELLABLE,
        INSUFFICIENT_QUANTITY,
        DUPLICATE_REQUEST
    }
}
