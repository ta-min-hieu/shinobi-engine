package dev.kage.friend.domain;

/** Chỉ bạn bè được nhắn riêng (ADR-402) — bảng {@code whisper_friends_only}: có dòng = đang bật. */
public interface WhisperPrivacyPort {

    /** Có đang bật không. */
    boolean friendsOnly(long characterId);

    /** Bật / tắt (lặp lại không sao). */
    void setFriendsOnly(long characterId, boolean enabled);
}
