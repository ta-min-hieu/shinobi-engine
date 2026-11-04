package dev.kage.character.domain;

/** Soi trang bị (ADR-171) — người chơi có cho người khác xem bảng trang bị của mình không. Mặc định: cho. */
public interface InspectPrivacyPort {

    boolean hidden(long characterId);

    void setHidden(long characterId, boolean hidden);
}
