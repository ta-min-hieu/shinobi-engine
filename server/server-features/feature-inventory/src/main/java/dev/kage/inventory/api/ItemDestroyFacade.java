package dev.kage.inventory.api;

import dev.kage.inventory.domain.ItemDestroyPort;

/** API huỷ vật phẩm (ADR-231). */
public final class ItemDestroyFacade {

    /** Không huỷ được gì (composition test). */
    public static final ItemDestroyFacade DISABLED =
            new ItemDestroyFacade((characterId, itemId) -> ItemDestroyPort.Outcome.NOT_FOUND);

    private final ItemDestroyPort port;

    /** Dựng ở composition root. */
    public ItemDestroyFacade(ItemDestroyPort port) {
        this.port = port;
    }

    /** Huỷ cả chồng {@code itemId} của {@code characterId}. */
    public ItemDestroyPort.Outcome destroy(long characterId, long itemId) {
        return port.destroy(characterId, itemId);
    }
}
