package dev.kage.character.api;

import dev.kage.character.domain.PvpStatsPort;

/** API thống kê PvP ngoài đấu trường (ADR-378). Chạm DB — Virtual Thread. */
public final class PvpStatsFacade {

    /** Không lưu gì (composition test). */
    public static final PvpStatsFacade DISABLED = new PvpStatsFacade(new PvpStatsPort() {
        @Override
        public void recordKill(long killerId, long victimId) {
            // không lưu
        }

        @Override
        public Stats stats(long characterId) {
            return new Stats(0, 0);
        }
    });

    private final PvpStatsPort port;

    /** Dựng ở composition root. */
    public PvpStatsFacade(PvpStatsPort port) {
        this.port = port;
    }

    /** Ghi một lần hạ người chơi (bỏ qua tự hạ mình). */
    public void recordKill(long killerId, long victimId) {
        if (killerId != victimId) {
            port.recordKill(killerId, victimId);
        }
    }

    /** Số liệu của nhân vật. */
    public PvpStatsPort.Stats stats(long characterId) {
        return port.stats(characterId);
    }
}
