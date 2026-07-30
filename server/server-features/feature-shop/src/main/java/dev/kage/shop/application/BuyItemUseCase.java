package dev.kage.shop.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.shop.domain.IdempotencyKeyReusedException;
import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;

/**
 * Mua item từ shop: ghi {@code idempotencyKey} TRƯỚC (ADR-025 — chống double-effect khi client tự
 * retry), trừ gold (rẻ, no-op nếu không đủ), rồi cộng item — nếu túi đầy, ném
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

    public BuyResult execute(long characterId, long itemTemplateId, int quantity, String idempotencyKey) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity phải > 0: " + quantity);
        }
        ShopListing listing = ports.catalogPort().findListing(itemTemplateId).orElse(null);
        if (listing == null) {
            return BuyResult.ITEM_NOT_IN_SHOP;
        }
        long totalCost = listing.buyPrice() * quantity;

        AtomicReference<BuyResult> resultHolder = new AtomicReference<BuyResult>();
        try {
            tx.inTransaction((Transaction t) -> {
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                if (!ports.goldPort().deductGold(t, characterId, totalCost)) {
                    resultHolder.set(BuyResult.INSUFFICIENT_GOLD);
                    return;
                }
                if (!ports.inventoryGatewayPort().grantItem(t, characterId, itemTemplateId, quantity)) {
                    throw new ShopTransactionAbortedException();
                }
                String detail = "{\"itemTemplateId\":%d,\"quantity\":%d,\"goldCost\":%d}"
                        .formatted(itemTemplateId, quantity, totalCost);
                ports.auditPort().log(t, characterId, "SHOP_BUY", detail);
                resultHolder.set(BuyResult.OK);
            });
        } catch (IdempotencyKeyReusedException e) {
            return BuyResult.DUPLICATE_REQUEST;
        } catch (ShopTransactionAbortedException e) {
            return BuyResult.INVENTORY_FULL;
        }
        return resultHolder.get();
    }

    public enum BuyResult {
        OK,
        ITEM_NOT_IN_SHOP,
        INSUFFICIENT_GOLD,
        INVENTORY_FULL,
        DUPLICATE_REQUEST
    }
}
