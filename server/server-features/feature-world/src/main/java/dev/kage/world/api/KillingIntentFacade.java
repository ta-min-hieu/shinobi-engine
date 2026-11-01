package dev.kage.world.api;

import java.time.Instant;
import java.util.Set;

import dev.kage.world.domain.KillingIntentPort;

/** Sát khí (ADR-142). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class KillingIntentFacade {

    /** Composition không có sát khí (test) — không ai bị tính, không ai là Ma Đầu. */
    public static final KillingIntentFacade DISABLED = new KillingIntentFacade(new KillingIntentPort() {
        @Override
        public int points(long characterId, Instant now) {
            return 0;
        }

        @Override
        public int recordKill(long killerId, Instant now) {
            return 0;
        }

        @Override
        public Set<Long> outlaws(Instant now) {
            return Set.of();
        }

        @Override
        public long dropGold(long victimId, long killerId, Instant now) {
            return 0L;
        }
    });

    private final KillingIntentPort port;

    /** Dựng ở composition root. */
    public KillingIntentFacade(KillingIntentPort port) {
        this.port = port;
    }

    /** Sát khí hiện tại. */
    public int points(long characterId, Instant now) {
        return port.points(characterId, now);
    }

    /** Hạ một người trong dải PvP — trả sát khí mới. */
    public int recordKill(long killerId, Instant now) {
        return port.recordKill(killerId, now);
    }

    /** Ma Đầu đang có. */
    public Set<Long> outlaws(Instant now) {
        return port.outlaws(now);
    }

    /** Ma Đầu chết — vàng rơi (về người hạ nếu là người chơi). */
    public long dropGold(long victimId, long killerId, Instant now) {
        return port.dropGold(victimId, killerId, now);
    }
}
