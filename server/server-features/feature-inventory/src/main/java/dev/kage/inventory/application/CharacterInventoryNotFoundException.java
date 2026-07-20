package dev.kage.inventory.application;

/** Nhân vật không có inventory — thường là dữ liệu hỏng, không phải luồng bình thường. */
public final class CharacterInventoryNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CharacterInventoryNotFoundException(long characterId) {
        super("Không tìm thấy inventory của characterId=" + characterId);
    }
}
