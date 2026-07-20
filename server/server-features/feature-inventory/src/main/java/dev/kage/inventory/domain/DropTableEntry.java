package dev.kage.inventory.domain;

/**
 * 1 dòng trong bảng rơi đồ (DATABASE.md §"drop_table" — "config, không code"). Mỗi entry được roll
 * ĐỘC LẬP (không phải rút thăm loại trừ lẫn nhau) — nhiều entry có thể cùng rơi 1 lúc, hoặc không
 * entry nào rơi cả.
 */
public record DropTableEntry(long itemTemplateId, int dropChancePercent, int minQuantity, int maxQuantity) {

    public DropTableEntry {
        if (itemTemplateId <= 0) {
            throw new IllegalArgumentException("itemTemplateId phải > 0: " + itemTemplateId);
        }
        if (dropChancePercent < 0 || dropChancePercent > 100) {
            throw new IllegalArgumentException("dropChancePercent ngoài [0,100]: " + dropChancePercent);
        }
        if (minQuantity <= 0 || maxQuantity < minQuantity) {
            throw new IllegalArgumentException(
                    "quantity range không hợp lệ: min=" + minQuantity + " max=" + maxQuantity);
        }
    }
}
