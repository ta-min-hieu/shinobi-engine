package dev.kage.inventory.api;

import java.time.Instant;

import dev.kage.inventory.domain.GemRemovalPort;

/** Tháo Linh Ngọc (ADR-168). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GemRemovalFacade {

    /** Phí mỗi lần tháo — PLACEHOLDER (Điều 27); khảm tốn 200, tháo đắt hơn để đổi ngọc là một lựa chọn có giá. */
    public static final long UNSOCKET_FEE = 500L;

    /** Composition không có tháo ngọc (test). */
    public static final GemRemovalFacade DISABLED = new GemRemovalFacade(
            (characterId, itemId, fee, at) -> new GemRemovalPort.Removed(GemRemovalPort.Outcome.NOT_FOUND, 0L));

    private final GemRemovalPort port;

    /** Dựng ở composition root. */
    public GemRemovalFacade(GemRemovalPort port) {
        this.port = port;
    }

    /** Tháo viên ngọc đang khảm trên món {@code itemId} của chính nhân vật. */
    public GemRemovalPort.Removed unsocket(long characterId, long itemId, Instant at) {
        return port.unsocket(characterId, itemId, UNSOCKET_FEE, at);
    }
}
