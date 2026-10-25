package dev.kage.achievement.api;

import java.util.List;

import dev.kage.achievement.domain.ReputationPort;

/** API danh vọng tông môn (ADR-109). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class ReputationFacade {

    /** Không có danh vọng (test). */
    public static final ReputationFacade DISABLED = new ReputationFacade(ReputationPort.NONE);

    private final ReputationPort port;

    /** Dựng ở composition root. */
    public ReputationFacade(ReputationPort port) {
        this.port = port;
    }

    /** Cộng điểm; {@code points <= 0} bị bỏ qua. */
    public void add(long characterId, int points) {
        if (points > 0) {
            port.add(characterId, points);
        }
    }

    /** Điểm hiện tại. */
    public ReputationPort.Standing standingOf(long characterId) {
        return port.standingOf(characterId);
    }

    /** Cửa hàng danh vọng. */
    public List<ReputationPort.ShopItem> shop() {
        return port.shop();
    }

    /** Mua một món. */
    public ReputationPort.BuyOutcome buy(long characterId, long itemTemplateId) {
        return port.buy(characterId, itemTemplateId);
    }
}
