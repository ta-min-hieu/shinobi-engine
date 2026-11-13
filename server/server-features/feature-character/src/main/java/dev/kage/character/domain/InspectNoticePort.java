package dev.kage.character.domain;

/** Báo khi bị soi trang bị (ADR-330) — bảng {@code inspect_notice_mute}; mặc định bật. */
public interface InspectNoticePort {

    /** {@code true} nếu nhân vật đã tắt báo. */
    boolean muted(long characterId);

    /** Tắt ({@code true}) hoặc bật lại báo. */
    void setMuted(long characterId, boolean muted);
}
