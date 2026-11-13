package dev.kage.character.api;

import java.util.List;

import dev.kage.character.domain.DeathLogPort;

/** API lịch sử tử vong (ADR-337): ghi mỗi lần bị hạ, xem {@value #SHOWN} lần gần nhất. Chạm DB — Virtual Thread. */
public final class DeathLogFacade {

    /** Số lần trả cho người chơi. */
    public static final int SHOWN = 10;

    /** Không lưu gì (composition test). */
    public static final DeathLogFacade DISABLED = new DeathLogFacade(new DeathLogPort() {
        @Override
        public void record(long characterId, Entry entry) {
            // không lưu
        }

        @Override
        public List<Entry> recent(long characterId, int limit) {
            return List.of();
        }
    });

    private final DeathLogPort port;

    /** Dựng ở composition root. */
    public DeathLogFacade(DeathLogPort port) {
        this.port = port;
    }

    /** Ghi một lần bị hạ. */
    public void record(long characterId, DeathLogPort.Entry entry) {
        port.record(characterId, entry);
    }

    /** {@value #SHOWN} lần gần nhất, mới trước. */
    public List<DeathLogPort.Entry> recent(long characterId) {
        return port.recent(characterId, SHOWN);
    }
}
