// TEMPLATE — Exception mang đủ context (CODING-STANDARDS §11): nói rõ AI gì, giá trị nào.
package dev.kage.inventory.application;

/** Nhân vật không có inventory — thường là dữ liệu hỏng, không phải luồng bình thường. */
public final class CharacterInventoryNotFoundException extends RuntimeException {
    public CharacterInventoryNotFoundException(long characterId) {
        super("Không tìm thấy inventory của characterId=" + characterId);
    }
}
