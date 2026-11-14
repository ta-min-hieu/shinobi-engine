package dev.kage.friend.api;

import java.util.List;

import dev.kage.friend.domain.IntimacyRankingPort;

/** API xếp hạng thân mật (ADR-434). Chạm DB — Virtual Thread. */
public final class IntimacyRankingFacade {

    /** Số cặp mỗi bảng. */
    public static final int TOP = 10;

    /** Bảng rỗng (composition test). */
    public static final IntimacyRankingFacade DISABLED = new IntimacyRankingFacade(limit -> List.of());

    private final IntimacyRankingPort port;

    /** Dựng ở composition root. */
    public IntimacyRankingFacade(IntimacyRankingPort port) {
        this.port = port;
    }

    /** Top {@value #TOP} cặp. */
    public List<IntimacyRankingPort.Pair> top() {
        return port.top(TOP);
    }
}
