package dev.kage.pvp.api;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import dev.kage.pvp.domain.HonorPort;
import dev.kage.pvp.domain.HonorRules;

/** Điểm vinh dự đấu trường + cửa hàng (ADR-155). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class HonorFacade {

    /** Composition không có điểm vinh dự (test). */
    public static final HonorFacade DISABLED = new HonorFacade(new HonorPort() {
        @Override
        public int award(long characterId, int points, LocalDate day) {
            return 0;
        }

        @Override
        public Shop shop(long characterId, LocalDate weekStart) {
            return new Shop(0L, List.of());
        }

        @Override
        public Outcome buy(long characterId, long itemTemplateId, LocalDate weekStart, Instant now) {
            return Outcome.UNKNOWN_ITEM;
        }
    });

    private final HonorPort port;

    /** Dựng ở composition root. */
    public HonorFacade(HonorPort port) {
        this.port = port;
    }

    /**
     * Một trận đấu trường vừa chốt: người thắng +{@value HonorRules#WIN_POINTS}, người thua
     * +{@value HonorRules#LOSS_POINTS}.
     */
    public void onDuelSettled(long winnerId, long loserId, Instant now) {
        port.award(winnerId, HonorRules.WIN_POINTS, HonorRules.day(now));
        port.award(loserId, HonorRules.LOSS_POINTS, HonorRules.day(now));
    }

    /** Cửa hàng tuần này. */
    public HonorPort.Shop shop(long characterId, Instant now) {
        return port.shop(characterId, HonorRules.weekStart(now));
    }

    /** Đổi một lần. */
    public HonorPort.Outcome buy(long characterId, long itemTemplateId, Instant now) {
        return port.buy(characterId, itemTemplateId, HonorRules.weekStart(now), now);
    }
}
