package dev.kage.friend.api;

import dev.kage.friend.domain.WhisperPrivacyPort;

/** API "chỉ bạn bè được nhắn riêng" (ADR-402). Chạm DB — Virtual Thread. */
public final class WhisperPrivacyFacade {

    /** Không ai bật (composition test). */
    public static final WhisperPrivacyFacade DISABLED = new WhisperPrivacyFacade(new WhisperPrivacyPort() {
        @Override
        public boolean friendsOnly(long characterId) {
            return false;
        }

        @Override
        public void setFriendsOnly(long characterId, boolean enabled) {
            // không lưu
        }
    });

    private final WhisperPrivacyPort port;

    /** Dựng ở composition root. */
    public WhisperPrivacyFacade(WhisperPrivacyPort port) {
        this.port = port;
    }

    /** Có đang bật không. */
    public boolean friendsOnly(long characterId) {
        return port.friendsOnly(characterId);
    }

    /** Bật / tắt. */
    public void setFriendsOnly(long characterId, boolean enabled) {
        port.setFriendsOnly(characterId, enabled);
    }

    /** Luật: tắt ⇒ ai cũng nhắn được; bật ⇒ chỉ bạn bè hoặc người cùng bang. Tĩnh để test. */
    public static boolean allows(boolean friendsOnly, boolean friends, boolean guildmates) {
        return !friendsOnly || friends || guildmates;
    }
}
