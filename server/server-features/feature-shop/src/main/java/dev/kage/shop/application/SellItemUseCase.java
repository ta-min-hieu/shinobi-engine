package dev.kage.shop.application;

import java.util.concurrent.atomic.AtomicReference;

import dev.kage.shop.domain.IdempotencyKeyReusedException;
import dev.kage.shop.domain.OwnedItem;
import dev.kage.shop.domain.RemoveItemOutcome;
import dev.kage.shop.domain.ShopListing;
import dev.kage.shop.domain.Transaction;
import dev.kage.shop.domain.TransactionRunner;

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
        OwnedItem owned =
                ports.inventoryGatewayPort().findItem(characterId, itemId).orElse(null);
        if (owned == null) {
            return SellResult.ITEM_NOT_FOUND;
        }
        ShopListing listing =
                ports.catalogPort().findListing(owned.itemTemplateId()).orElse(null);
        if (listing == null) {
            return SellResult.ITEM_NOT_SELLABLE;
        }
        long totalCredit = listing.totalSellCredit(quantity);

        AtomicReference<SellResult> resultHolder = new AtomicReference<SellResult>();
        try {
            tx.inTransaction((Transaction t) -> {
                // Khoá túi đồ ĐẦU TIÊN, trước vàng — thứ tự khoá chung (DATABASE.md §5.1).
                ports.inventoryGatewayPort().lockInventory(t, characterId);
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                RemoveItemOutcome removed =
                        ports.inventoryGatewayPort().removeUnequippedQuantity(t, characterId, itemId, quantity);
                if (removed != RemoveItemOutcome.OK) {
                    resultHolder.set(toSellResult(removed));
                    return;
                }
                ports.goldPort().creditGold(t, characterId, totalCredit);
                // itemTemplateId (dòng 66(b), 2026-10-17): itemId là dòng túi đồ — dòng đó có thể đã biến
                // mất khi bán hết, nên một mình nó không dựng lại được LOẠI vật phẩm rời khỏi game.
                String detail = "{\"itemId\":%d,\"itemTemplateId\":%d,\"quantity\":%d,\"goldEarned\":%d}"
                        .formatted(itemId, owned.itemTemplateId(), quantity, totalCredit);
                ports.auditPort().log(t, characterId, "SHOP_SELL", detail);
                resultHolder.set(SellResult.OK);
            });
        } catch (IdempotencyKeyReusedException e) {
            return SellResult.DUPLICATE_REQUEST;
        }
        return resultHolder.get();
    }

    private static SellResult toSellResult(RemoveItemOutcome removed) {
        return switch (removed) {
            case OK -> SellResult.OK;
            case INSUFFICIENT_QUANTITY -> SellResult.INSUFFICIENT_QUANTITY;
            case ITEM_EQUIPPED -> SellResult.ITEM_EQUIPPED;
            case ITEM_IN_TRADE -> SellResult.ITEM_IN_TRADE;
        };
    }

    public enum SellResult {
        OK,
        ITEM_NOT_FOUND,
        ITEM_NOT_SELLABLE,
        INSUFFICIENT_QUANTITY,
        DUPLICATE_REQUEST,
        /** Đang mặc — phải gỡ ra trước (quyết định chủ dự án 2026-09-24). */
        ITEM_EQUIPPED,
        /** Phần muốn bán đụng vào chỗ đang chào trong giao dịch (dòng 80, chủ dự án duyệt 2026-10-18). */
        ITEM_IN_TRADE
    }
}
