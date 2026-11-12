package dev.kage.shop.api;

import java.util.List;

import dev.kage.shop.domain.PurchaseHistoryPort;

/** API lịch sử mua cửa hàng NPC (ADR-288): {@value #LIMIT} lần gần nhất. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class PurchaseHistoryFacade {

    /** Số lần mua trả về. */
    public static final int LIMIT = 20;

    /** Không có lịch sử (composition test). */
    public static final PurchaseHistoryFacade DISABLED = new PurchaseHistoryFacade((id, limit) -> List.of());

    private final PurchaseHistoryPort port;

    /** Dựng ở composition root. */
    public PurchaseHistoryFacade(PurchaseHistoryPort port) {
        this.port = port;
    }

    /** Các lần mua gần nhất của {@code characterId}. */
    public List<PurchaseHistoryPort.Purchase> recent(long characterId) {
        return port.recent(characterId, LIMIT);
    }
}
