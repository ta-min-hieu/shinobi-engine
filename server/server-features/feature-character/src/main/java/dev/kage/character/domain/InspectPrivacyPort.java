package dev.kage.character.domain;

/** Soi trang bị (ADR-171) — người chơi có cho người khác xem bảng trang bị của mình không. Mặc định: cho. */
public interface InspectPrivacyPort {

    boolean hidden(long characterId);

    void setHidden(long characterId, boolean hidden);

    /** ADR-218 — dòng giới thiệu tự viết ("" = chưa viết). */
    String bio(long characterId);

    /** ADR-218 — ghi dòng giới thiệu (đã kiểm độ dài / ký tự ở facade). */
    void setBio(long characterId, String bio);
}
