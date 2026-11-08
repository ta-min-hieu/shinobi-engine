package dev.kage.inventory.api;

import dev.kage.inventory.domain.StackSplitPort;

/** API tách chồng vật phẩm (ADR-215). */
public final class StackSplitFacade {

    /** Không tách được gì (composition test). */
    public static final StackSplitFacade DISABLED =
            new StackSplitFacade((characterId, inventoryItemId, amount) -> StackSplitPort.Outcome.NOT_FOUND);

    private final StackSplitPort port;

    /** Dựng ở composition root. */
    public StackSplitFacade(StackSplitPort port) {
        this.port = port;
    }

    /** Tách {@code amount} cái (≥ 1) khỏi chồng {@code inventoryItemId} sang ô mới. */
    public StackSplitPort.Outcome split(long characterId, long inventoryItemId, int amount) {
        if (amount <= 0) {
            return StackSplitPort.Outcome.INVALID_AMOUNT;
        }
        return port.split(characterId, inventoryItemId, amount);
    }
}
