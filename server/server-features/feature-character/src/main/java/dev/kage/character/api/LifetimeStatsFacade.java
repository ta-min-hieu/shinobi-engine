package dev.kage.character.api;

import dev.kage.character.domain.LifetimeStatsPort;

/** API thống kê cả đời (ADR-223). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class LifetimeStatsFacade {

    /** Không lưu gì (composition test). */
    public static final LifetimeStatsFacade DISABLED = new LifetimeStatsFacade(new LifetimeStatsPort() {
        @Override
        public void addDeath(long characterId) {
            // không lưu
        }

        @Override
        public void addPlaytime(long characterId, long seconds) {
            // không lưu
        }

        @Override
        public Stats of(long characterId) {
            return Stats.NONE;
        }
    });

    private final LifetimeStatsPort port;

    /** Dựng ở composition root. */
    public LifetimeStatsFacade(LifetimeStatsPort port) {
        this.port = port;
    }

    /** Cộng một lần bị hạ. */
    public void addDeath(long characterId) {
        port.addDeath(characterId);
    }

    /** Cộng {@code seconds} giây trong world (bỏ qua số không dương). */
    public void addPlaytime(long characterId, long seconds) {
        if (seconds > 0) {
            port.addPlaytime(characterId, seconds);
        }
    }

    /** Số liệu đã lưu. */
    public LifetimeStatsPort.Stats of(long characterId) {
        return port.of(characterId);
    }
}
