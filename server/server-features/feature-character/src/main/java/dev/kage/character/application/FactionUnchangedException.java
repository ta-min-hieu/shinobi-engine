package dev.kage.character.application;

/** Chọn đúng linh căn đang có — không tính là một lần đổi, không tốn gì (ADR-066). */
public final class FactionUnchangedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FactionUnchangedException(long characterId) {
        super("Nhân vật " + characterId + " đã ở linh căn này");
    }
}
