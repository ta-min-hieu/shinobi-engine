package dev.kage.character.api;

import java.time.Instant;
import java.util.Collection;
import java.util.Map;

import dev.kage.character.domain.LastOnlinePort;

/** Lần cuối online (ADR-192). Chạm DB — Virtual Thread. */
public final class LastOnlineFacade {

    /** Composition không có tính năng này (test): không ghi, không biết gì. */
    public static final LastOnlineFacade DISABLED = new LastOnlineFacade(new LastOnlinePort() {
        @Override
        public void markLeft(long characterId, Instant at) {
            // không ghi
        }

        @Override
        public Map<Long, Instant> lastOnlineOf(Collection<Long> characterIds) {
            return Map.of();
        }
    });

    private final LastOnlinePort port;

    /** Dựng ở composition root. */
    public LastOnlineFacade(LastOnlinePort port) {
        this.port = port;
    }

    /** Nhân vật vừa rời world lúc {@code at}. */
    public void markLeft(long characterId, Instant at) {
        port.markLeft(characterId, at);
    }

    /** Mốc rời world của những người có mốc — một câu cho cả danh sách. */
    public Map<Long, Instant> lastOnlineOf(Collection<Long> characterIds) {
        return characterIds.isEmpty() ? Map.of() : port.lastOnlineOf(characterIds);
    }
}
