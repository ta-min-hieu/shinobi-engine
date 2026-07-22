package dev.kage.inventory.domain;

/**
 * Dữ liệu TĨNH của 1 loại item (DATABASE.md §2.2 {@code item_template}) — chỉ mang những gì
 * "mặc đồ"/"tính lại chỉ số dẫn xuất" cần ({@code equipSlot}, {@code bonusAtk}), KHÔNG mang
 * {@code nameKey}/{@code maxStack}: chưa có UseCase nào cần đọc lại 2 trường đó (YAGNI — thêm khi
 * có nhu cầu thật, không suy đoán trước).
 *
 * <p>{@code equipSlot == null} nghĩa là item KHÔNG mặc được (VD "kunai" — vật phẩm tiêu hao), lúc
 * đó {@code bonusAtk} luôn 0 (item không mặc được thì không cộng chỉ số — validate trong compact
 * constructor). {@code bonusAtk} là CHỈ SỐ DUY NHẤT trang bị cộng được ở lát cắt này (GDD §5 còn
 * SKILL_DMG/DEF/RES/... — thêm khi có item thật cần, tránh field không ai dùng).
 */
public record ItemTemplate(long id, EquipmentSlotType equipSlot, int bonusAtk) {

    public ItemTemplate {
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
        if (equipSlot == null && bonusAtk != 0) {
            throw new IllegalArgumentException("item không mặc được (equipSlot=null) không được có bonusAtk != 0");
        }
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }
}
