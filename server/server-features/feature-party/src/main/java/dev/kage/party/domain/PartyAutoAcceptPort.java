package dev.kage.party.domain;

/** Tự nhận lời mời nhóm từ bạn bè (ADR-286) — một công tắc mỗi nhân vật. */
public interface PartyAutoAcceptPort {

    /** {@code characterId} có đang bật không (không có nhân vật ⇒ {@code false}). */
    boolean enabled(long characterId);

    /** Bật / tắt. */
    void setEnabled(long characterId, boolean enabled);
}
