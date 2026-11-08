package dev.kage.character.api;

import java.util.Map;

import dev.kage.character.domain.WealthRankingPort;

/** API bảng giàu có (ADR-241): {@value #TOP} người nhiều linh thạch nhất. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class WealthRankingFacade {

    /** Số dòng tối đa của bảng. */
    public static final int TOP = 100;

    /** Bảng rỗng (composition test). */
    public static final WealthRankingFacade DISABLED = new WealthRankingFacade(limit -> Map.of());

    private final WealthRankingPort port;

    /** Dựng ở composition root. */
    public WealthRankingFacade(WealthRankingPort port) {
        this.port = port;
    }

    /** Linh thạch theo nhân vật, chỉ {@value #TOP} người đứng đầu. */
    public Map<Long, Long> richest() {
        return port.richest(TOP);
    }
}
