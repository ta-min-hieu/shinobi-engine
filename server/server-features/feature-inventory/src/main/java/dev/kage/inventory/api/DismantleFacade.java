package dev.kage.inventory.api;

import dev.kage.inventory.domain.DismantlePort;

/** API phân giải pháp khí (ADR-097). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DismantleFacade {

    /** Composition không có phân giải (test) — không món nào thuộc về ai. */
    public static final DismantleFacade DISABLED =
            new DismantleFacade((characterId, itemId) -> DismantlePort.Result.refused(DismantlePort.Outcome.NOT_FOUND));

    private final DismantlePort port;

    /** Dựng ở composition root. */
    public DismantleFacade(DismantlePort port) {
        this.port = port;
    }

    /** Phân giải món {@code itemId} của chính mình — {@code characterId} từ session. */
    public DismantlePort.Result dismantle(long characterId, long itemId) {
        return port.dismantle(characterId, itemId);
    }
}
