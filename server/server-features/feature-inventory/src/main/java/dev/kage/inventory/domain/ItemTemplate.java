package dev.kage.inventory.domain;

/**
 * Dữ liệu TĨNH của 1 loại item (DATABASE.md §2.2 {@code item_template}) — chỉ mang những gì
 * "mặc đồ" cần ({@code equipSlot}), KHÔNG mang {@code nameKey}/{@code maxStack}: chưa có UseCase
 * nào cần đọc lại 2 trường đó (YAGNI — thêm khi có nhu cầu thật, không suy đoán trước).
 *
 * <p>{@code equipSlot == null} nghĩa là item KHÔNG mặc được (VD "kunai" — vật phẩm tiêu hao).
 */
public record ItemTemplate(long id, EquipmentSlotType equipSlot) {

    public ItemTemplate {
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }
}
