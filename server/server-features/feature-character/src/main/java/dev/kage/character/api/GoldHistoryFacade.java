package dev.kage.character.api;

import java.util.List;

import dev.kage.character.domain.GoldHistoryPort;

/** Sổ vàng cá nhân (ADR-169). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class GoldHistoryFacade {

    /** Số dòng mỗi lần xem. */
    public static final int PAGE = 50;

    /** Composition không có sổ vàng (test). */
    public static final GoldHistoryFacade DISABLED = new GoldHistoryFacade((characterId, limit) -> List.of());

    private final GoldHistoryPort port;

    /** Dựng ở composition root. */
    public GoldHistoryFacade(GoldHistoryPort port) {
        this.port = port;
    }

    /** {@value #PAGE} lần vàng đổi gần nhất, mới nhất trước. */
    public List<GoldHistoryPort.Entry> recent(long characterId) {
        return port.recent(characterId, PAGE);
    }
}
