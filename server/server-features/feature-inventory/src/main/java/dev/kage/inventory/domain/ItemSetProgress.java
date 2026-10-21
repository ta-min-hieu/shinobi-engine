package dev.kage.inventory.domain;

/**
 * Một bộ pháp khí mà nhân vật đang mặc ÍT NHẤT một món (ADR-065) — cho bảng nhân vật.
 *
 * @param nameKey khoá i18n tên bộ
 * @param wornPieces số mẫu KHÁC NHAU của bộ đang mặc
 * @param totalPieces số mẫu của bộ
 * @param activeBonus tổng thưởng các bậc đã đạt (có thể bằng 0)
 */
public record ItemSetProgress(String nameKey, int wornPieces, int totalPieces, EquipmentBonus activeBonus) {}
