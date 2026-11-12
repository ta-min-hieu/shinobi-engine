package dev.kage.pvp.api;

import java.util.List;

import dev.kage.pvp.domain.DuelHistoryPort;

/** API lịch sử đấu tay đôi (ADR-280): {@value #LIMIT} trận gần nhất. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class DuelHistoryFacade {

    /** Số trận trả về. */
    public static final int LIMIT = 10;

    /** Không có lịch sử (composition test). */
    public static final DuelHistoryFacade DISABLED = new DuelHistoryFacade((id, limit) -> List.of());

    private final DuelHistoryPort port;

    /** Dựng ở composition root. */
    public DuelHistoryFacade(DuelHistoryPort port) {
        this.port = port;
    }

    /** Các trận gần nhất của {@code characterId}. */
    public List<DuelHistoryPort.Duel> recent(long characterId) {
        return port.recent(characterId, LIMIT);
    }
}
