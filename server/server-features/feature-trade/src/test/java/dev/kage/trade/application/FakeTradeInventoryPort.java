package dev.kage.trade.application;

import dev.kage.trade.domain.OwnedItemSnapshot;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TradeInventoryPort;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

/** Fake in-memory THẬT — mô phỏng {@code inventory_item}, dùng chung cho test {@code application/}. */
final class FakeTradeInventoryPort implements TradeInventoryPort {

    private final Map<Long, OwnedItemSnapshot> items = new LinkedHashMap<>();
    private final AtomicLong nextItemId = new AtomicLong(1);
    private final Set<Long> fullInventoryCharacterIds = new HashSet<>();

    long addItem(long characterId, long itemTemplateId, int quantity, int enhanceLevel) {
        long id = nextItemId.getAndIncrement();
        items.put(id, new OwnedItemSnapshot(id, characterId, itemTemplateId, quantity, enhanceLevel));
        return id;
    }

    void markInventoryFull(long characterId) {
        fullInventoryCharacterIds.add(characterId);
    }

    @Override
    public Optional<OwnedItemSnapshot> findItemById(long itemId) {
        return Optional.ofNullable(items.get(itemId));
    }

    @Override
    public TransferOutcome transferItem(Transaction tx, TransferRequest request) {
        var current = items.get(request.itemId());
        if (current == null || current.characterId() != request.fromCharacterId()) {
            return TransferOutcome.ITEM_NOT_OWNED;
        }
        if (current.quantity() < request.quantity()) {
            return TransferOutcome.INSUFFICIENT_QUANTITY;
        }
        if (fullInventoryCharacterIds.contains(request.toCharacterId())) {
            return TransferOutcome.RECIPIENT_INVENTORY_FULL;
        }
        if (current.quantity() == request.quantity()) {
            items.put(request.itemId(), new OwnedItemSnapshot(
                    request.itemId(), request.toCharacterId(), current.itemTemplateId(), request.quantity(),
                    current.enhanceLevel()));
        } else {
            items.put(request.itemId(), new OwnedItemSnapshot(
                    request.itemId(), request.fromCharacterId(), current.itemTemplateId(),
                    current.quantity() - request.quantity(), current.enhanceLevel()));
            long newId = nextItemId.getAndIncrement();
            items.put(newId, new OwnedItemSnapshot(
                    newId, request.toCharacterId(), current.itemTemplateId(), request.quantity(),
                    current.enhanceLevel()));
        }
        return TransferOutcome.OK;
    }
}
