package dev.kage.character.api;

import dev.kage.character.domain.InspectNoticePort;

/** API báo khi bị soi trang bị (ADR-330): người bị soi nhận một dòng báo, trừ khi đã tắt. Chạm DB — Virtual Thread. */
public final class InspectNoticeFacade {

    /** Không lưu gì; coi như đã tắt để composition test không gửi gì. */
    public static final InspectNoticeFacade DISABLED = new InspectNoticeFacade(new InspectNoticePort() {
        @Override
        public boolean muted(long characterId) {
            return true;
        }

        @Override
        public void setMuted(long characterId, boolean muted) {
            // không lưu
        }
    });

    private final InspectNoticePort port;

    /** Dựng ở composition root. */
    public InspectNoticeFacade(InspectNoticePort port) {
        this.port = port;
    }

    /** {@code true} nếu nhân vật muốn được báo. */
    public boolean enabled(long characterId) {
        return !port.muted(characterId);
    }

    /** Bật / tắt báo. */
    public void setEnabled(long characterId, boolean enabled) {
        port.setMuted(characterId, !enabled);
    }
}
