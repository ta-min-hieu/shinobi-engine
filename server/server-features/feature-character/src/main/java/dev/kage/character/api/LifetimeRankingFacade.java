package dev.kage.character.api;

import java.util.List;

import dev.kage.character.domain.LifetimeRankingPort;

/** API xếp hạng trọn đời (ADR-391 sát thủ PvP, ADR-392 thời gian tu luyện). Chạm DB — Virtual Thread. */
public final class LifetimeRankingFacade {

    /** Số dòng mỗi bảng. */
    public static final int TOP = 10;

    /** Bảng rỗng (composition test). */
    public static final LifetimeRankingFacade DISABLED = new LifetimeRankingFacade(new LifetimeRankingPort() {
        @Override
        public List<Ranked> topPlayerKills(int limit) {
            return List.of();
        }

        @Override
        public List<Ranked> topPlaytime(int limit) {
            return List.of();
        }
    });

    private final LifetimeRankingPort port;

    /** Dựng ở composition root. */
    public LifetimeRankingFacade(LifetimeRankingPort port) {
        this.port = port;
    }

    /** ADR-391 — top {@value #TOP} sát thủ. */
    public List<LifetimeRankingPort.Ranked> topPlayerKills() {
        return port.topPlayerKills(TOP);
    }

    /** ADR-392 — top {@value #TOP} thời gian tu luyện. */
    public List<LifetimeRankingPort.Ranked> topPlaytime() {
        return port.topPlaytime(TOP);
    }
}
