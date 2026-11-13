package dev.kage.character.api;

import java.util.List;

import dev.kage.character.domain.BreakthroughLogPort;

/** API lịch sử đột phá cảnh giới (ADR-322). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class BreakthroughLogFacade {

    /** Không lưu gì (composition test). */
    public static final BreakthroughLogFacade DISABLED = new BreakthroughLogFacade(new BreakthroughLogPort() {
        @Override
        public void record(long characterId, int realm, int level) {
            // không lưu gì
        }

        @Override
        public List<Entry> history(long characterId) {
            return List.of();
        }
    });

    private final BreakthroughLogPort port;

    /** Dựng ở composition root. */
    public BreakthroughLogFacade(BreakthroughLogPort port) {
        this.port = port;
    }

    /** Ghi một lần đột phá. */
    public void record(long characterId, int realm, int level) {
        port.record(characterId, realm, level);
    }

    /** Lịch sử, cũ trước. */
    public List<BreakthroughLogPort.Entry> history(long characterId) {
        return port.history(characterId);
    }
}
