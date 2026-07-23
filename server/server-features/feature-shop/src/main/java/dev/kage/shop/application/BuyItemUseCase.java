package dev.kage.shop.application;

import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Mua item từ shop: trừ gold TRƯỚC (rẻ, no-op nếu không đủ), rồi cộng item — nếu túi đầy, ném
 * {@link ShopTransactionAbortedException} để rollback luôn phần gold đã trừ (KHÔNG được để "trừ
 * tiền nhưng không nhận item", DATABASE.md §5).
 */
public final class BuyItemUseCase {

    private final ShopPorts ports;
    private final TransactionRunner tx;

    public BuyItemUseCase(ShopPorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    public BuyResult execute(long characterId, long itemTemplateId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
        ShopListing listing = ports.catalogPort().findListing(itemTemplateId).orElse(null);
        if (listing == null) {
            return BuyResult.ITEM_NOT_IN_SHOP;
        }
        long totalCost = listing.buyPrice() * quantity;

        var resultHolder = new AtomicReference<BuyResult>();
        try {
            tx.inTransaction((Transaction t) -> {
                if (!ports.goldPort().deductGold(t, characterId, totalCost)) {
                    resultHolder.set(BuyResult.INSUFFICIENT_GOLD);
                    return;
                }
                if (!ports.inventoryGatewayPort().grantItem(t, characterId, itemTemplateId, quantity)) {
                    throw new ShopTransactionAbortedException();
                }
                var detail = "{\"itemTemplateId\":%d,\"quantity\":%d,\"goldCost\":%d}"
                        .formatted(itemTemplateId, quantity, totalCost);
                ports.auditPort().log(t, characterId, "SHOP_BUY", detail);
                resultHolder.set(BuyResult.OK);
            });
        } catch (ShopTransactionAbortedException e) {
            return BuyResult.INVENTORY_FULL;
        }
        return resultHolder.get();
    }

    public enum BuyResult { OK, ITEM_NOT_IN_SHOP, INSUFFICIENT_GOLD, INVENTORY_FULL }
}
