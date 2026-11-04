package dev.kage.character.api;

import dev.kage.character.domain.InspectPrivacyPort;

/** Soi trang bị (ADR-171) — công tắc riêng tư. Chạm DB — chỉ gọi trên Virtual Thread. */
public final class InspectPrivacyFacade {

    /** Composition không có công tắc (test): ai cũng xem được. */
    public static final InspectPrivacyFacade DISABLED = new InspectPrivacyFacade(new InspectPrivacyPort() {
        @Override
        public boolean hidden(long characterId) {
            return false;
        }

        @Override
        public void setHidden(long characterId, boolean hidden) {
            // không lưu
        }
    });

    private final InspectPrivacyPort port;

    /** Dựng ở composition root. */
    public InspectPrivacyFacade(InspectPrivacyPort port) {
        this.port = port;
    }

    /** Người này đã tắt cho xem chưa. */
    public boolean hidden(long characterId) {
        return port.hidden(characterId);
    }

    /** Bật/tắt cho xem. */
    public void setHidden(long characterId, boolean hidden) {
        port.setHidden(characterId, hidden);
    }
}
