package dev.kage.monster.api;

import java.util.List;

import dev.kage.monster.domain.BossKillRankingPort;

/** API xếp hạng hạ boss (ADR-442) — đếm audit {@code BOSS_KILL} (ADR-296). Chạm DB — Virtual Thread. */
public final class BossKillRankingFacade {

    /** Số dòng mỗi bảng. */
    public static final int TOP = 10;

    /** Bảng rỗng (composition test). */
    public static final BossKillRankingFacade DISABLED = new BossKillRankingFacade(limit -> List.of());

    private final BossKillRankingPort port;

    /** Dựng ở composition root. */
    public BossKillRankingFacade(BossKillRankingPort port) {
        this.port = port;
    }

    /** Top {@value #TOP}. */
    public List<BossKillRankingPort.Ranked> top() {
        return port.top(TOP);
    }
}
