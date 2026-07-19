package dev.kage.character.application;

/**
 * Nhân vật không tồn tại HOẶC không thuộc account đang chọn — ném CÙNG lỗi cho cả 2 trường hợp
 * (fail secure, không lộ thông tin nhân vật của account khác — SECURITY.md §4 chống IDOR).
 */
public final class CharacterNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CharacterNotFoundException(long characterId) {
        super("Không tìm thấy nhân vật thuộc account này: " + characterId);
    }
}
