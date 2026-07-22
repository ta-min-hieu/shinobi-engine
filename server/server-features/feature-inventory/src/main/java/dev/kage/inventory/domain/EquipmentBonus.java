package dev.kage.inventory.domain;

/**
 * Tổng chỉ số cộng thêm từ TOÀN BỘ trang bị đang mặc (GAMEPLAY.md §2 "tính lại chỉ số dẫn xuất
 * khi đổi trang bị") — LUÔN tính lại từ đầu ({@link ComputeEquipmentBonusUseCase}, không cộng dồn
 * delta), tránh trôi số nếu equip/gỡ đồ nhiều lần hoặc gọi sai thứ tự.
 *
 * <p>Chỉ có {@code bonusAtk} ở lát cắt này — {@link ItemTemplate} cũng chỉ mang đúng field này,
 * thêm chỉ số khác (SKILL_DMG/DEF/RES...) khi có item thật cần, không suy đoán trước (YAGNI).
 */
public record EquipmentBonus(int bonusAtk) {

    public static final EquipmentBonus NONE = new EquipmentBonus(0);
}
