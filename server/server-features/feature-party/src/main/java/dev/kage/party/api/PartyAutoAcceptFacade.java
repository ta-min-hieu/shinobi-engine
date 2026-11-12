package dev.kage.party.api;

import dev.kage.party.domain.PartyAutoAcceptPort;

/** API công tắc tự nhận lời mời nhóm từ bạn bè (ADR-286). Chạm DB — chỉ gọi trên Virtual Thread. */
public final class PartyAutoAcceptFacade {

    /** Không ai bật (composition test). */
    public static final PartyAutoAcceptFacade DISABLED = new PartyAutoAcceptFacade(new PartyAutoAcceptPort() {
        @Override
        public boolean enabled(long characterId) {
            return false;
        }

        @Override
        public void setEnabled(long characterId, boolean enabled) {
            // không lưu
        }
    });

    private final PartyAutoAcceptPort port;

    /** Dựng ở composition root. */
    public PartyAutoAcceptFacade(PartyAutoAcceptPort port) {
        this.port = port;
    }

    /** Đang bật không. */
    public boolean enabled(long characterId) {
        return port.enabled(characterId);
    }

    /** Bật / tắt. */
    public void setEnabled(long characterId, boolean enabled) {
        port.setEnabled(characterId, enabled);
    }
}
