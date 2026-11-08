package dev.kage.character.api;

import dev.kage.character.domain.StarterGiftPort;

/** API cờ gói quà tân thủ (ADR-227). */
public final class StarterGiftFacade {

    /** Không bao giờ gửi (composition test). */
    public static final StarterGiftFacade DISABLED = new StarterGiftFacade(characterId -> false);

    private final StarterGiftPort port;

    /** Dựng ở composition root. */
    public StarterGiftFacade(StarterGiftPort port) {
        this.port = port;
    }

    /** {@code true} nếu đây là lần đầu — người gọi gửi quà; {@code false} nếu đã gửi rồi. */
    public boolean markSent(long characterId) {
        return port.markSent(characterId);
    }
}
