package dev.kage.shop.application;

import dev.kage.shop.domain.IdempotencyKeyReusedException;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Bán item cho shop: ghi {@code idempotencyKey} TRƯỚC (ADR-025), bớt item (điều kiện, no-op nếu
 * không đủ số lượng), rồi cộng gold — {@code creditGold} không có nhánh thất bại nên không cần
 * rollback thủ công như {@link BuyItemUseCase}.
 */
public final class SellItemUseCase {

    private final ShopPorts ports;
    private final TransactionRunner tx;

    public SellItemUseCase(ShopPorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    public SellResult execute(long characterId, long itemId, int quantity, String idempotencyKey) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
        OwnedItem owned = ports.inventoryGatewayPort().findItem(characterId, itemId).orElse(null);
        if (owned == null) {
            return SellResult.ITEM_NOT_FOUND;
        }
        ShopListing listing = ports.catalogPort().findListing(owned.itemTemplateId()).orElse(null);
        if (listing == null) {
            return SellResult.ITEM_NOT_SELLABLE;
        }
        long totalCredit = listing.sellPrice() * quantity;

        var resultHolder = new AtomicReference<SellResult>();
        try {
            tx.inTransaction((Transaction t) -> {
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                if (!ports.inventoryGatewayPort().removeQuantity(t, characterId, itemId, quantity)) {
                    resultHolder.set(SellResult.INSUFFICIENT_QUANTITY);
                    return;
                }
                ports.goldPort().creditGold(t, characterId, totalCredit);
                var detail = "{\"itemId\":%d,\"quantity\":%d,\"goldEarned\":%d}"
                        .formatted(itemId, quantity, totalCredit);
                ports.auditPort().log(t, characterId, "SHOP_SELL", detail);
                resultHolder.set(SellResult.OK);
            });
        } catch (IdempotencyKeyReusedException e) {
            return SellResult.DUPLICATE_REQUEST;
        }
        return resultHolder.get();
    }

    public enum SellResult { OK, ITEM_NOT_FOUND, ITEM_NOT_SELLABLE, INSUFFICIENT_QUANTITY, DUPLICATE_REQUEST }
}
