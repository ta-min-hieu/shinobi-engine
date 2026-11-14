package dev.kage.friend.api;

import dev.kage.friend.domain.InvisibleModePort;

/** API chế độ ẩn (ADR-383): bạn bè thấy mình như offline. Chạm DB — Virtual Thread. */
public final class InvisibleModeFacade {

    /** Không ai ẩn (composition test). */
    public static final InvisibleModeFacade DISABLED = new InvisibleModeFacade(new InvisibleModePort() {
        @Override
        public boolean isInvisible(long characterId) {
            return false;
        }

        @Override
        public void setInvisible(long characterId, boolean invisible) {
            // không lưu
        }
    });

    private final InvisibleModePort port;

    /** Dựng ở composition root. */
    public InvisibleModeFacade(InvisibleModePort port) {
        this.port = port;
    }

    /** Nhân vật có đang ẩn không. */
    public boolean isInvisible(long characterId) {
        return port.isInvisible(characterId);
    }

    /** Bật / tắt ẩn. */
    public void setInvisible(long characterId, boolean invisible) {
        port.setInvisible(characterId, invisible);
    }
}
