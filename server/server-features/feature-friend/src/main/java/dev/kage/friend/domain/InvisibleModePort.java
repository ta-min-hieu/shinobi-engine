package dev.kage.friend.domain;

/** Chế độ ẩn (ADR-383) — bảng {@code invisible_mode}: có dòng = đang ẩn với bạn bè. */
public interface InvisibleModePort {

    /** Nhân vật có đang ẩn không. */
    boolean isInvisible(long characterId);

    /** Bật / tắt ẩn (lặp lại không sao). */
    void setInvisible(long characterId, boolean invisible);
}
