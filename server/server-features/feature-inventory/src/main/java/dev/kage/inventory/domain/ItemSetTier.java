package dev.kage.inventory.domain;

/**
 * Một bậc thưởng của bộ pháp khí (ADR-065): mặc đủ {@code piecesRequired} món KHÁC NHAU của bộ thì cộng
 * {@code bonus}. Các bậc cộng DỒN (mặc đủ 6 món nhận cả bậc 2, 4 và 6).
 */
public record ItemSetTier(int piecesRequired, EquipmentBonus bonus) {

    public ItemSetTier {
        if (piecesRequired < 1) {
            throw new IllegalArgumentException("piecesRequired phải >= 1: " + piecesRequired);
        }
        if (bonus == null) {
            throw new IllegalArgumentException("bonus không được null");
        }
    }
}
