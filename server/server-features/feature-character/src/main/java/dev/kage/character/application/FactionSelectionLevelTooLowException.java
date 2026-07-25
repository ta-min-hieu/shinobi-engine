package dev.kage.character.application;

/** Nhân vật chưa đủ Lv10 để chọn/đổi hệ phái (GDD §4 "chọn hệ ở Lv10"). */
public final class FactionSelectionLevelTooLowException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public FactionSelectionLevelTooLowException(long characterId, int currentLevel, int requiredLevel) {
        super("Nhân vật " + characterId + " chưa đủ cấp chọn hệ phái: hiện Lv" + currentLevel
                + ", cần Lv" + requiredLevel);
    }
}
