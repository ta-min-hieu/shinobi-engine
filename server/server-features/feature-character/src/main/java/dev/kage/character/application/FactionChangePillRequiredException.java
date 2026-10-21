package dev.kage.character.application;

/** Lần đổi linh căn này không miễn phí và túi không có Tẩy Căn Đan nào lấy được (ADR-066). */
public final class FactionChangePillRequiredException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FactionChangePillRequiredException(long characterId) {
        super("Nhân vật " + characterId + " cần một Tẩy Căn Đan để đổi linh căn");
    }
}
