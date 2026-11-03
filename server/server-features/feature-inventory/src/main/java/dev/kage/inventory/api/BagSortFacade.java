package dev.kage.inventory.api;

import dev.kage.inventory.domain.BagSortPort;

/** Sắp xếp túi đồ (ADR-160). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class BagSortFacade {

    /** Composition không có sắp xếp túi (test): không đổi gì. */
    public static final BagSortFacade DISABLED = new BagSortFacade(characterId -> 0);

    private final BagSortPort port;

    /** Dựng ở composition root. */
    public BagSortFacade(BagSortPort port) {
        this.port = port;
    }

    /** Gom chồng + xếp lại túi của chính nhân vật; trả số dòng đã gom mất. */
    public int sort(long characterId) {
        return port.sort(characterId);
    }
}
