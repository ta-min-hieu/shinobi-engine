package dev.kage.inventory.domain;

/**
 * Tổng chỉ số cộng thêm từ TOÀN BỘ trang bị đang mặc (GAMEPLAY.md §2 "tính lại chỉ số dẫn xuất
 * khi đổi trang bị") — LUÔN tính lại từ đầu ({@link ComputeEquipmentBonusUseCase}, không cộng dồn
 * delta), tránh trôi số nếu equip/gỡ đồ nhiều lần hoặc gọi sai thứ tự.
 *
 * <p>Bốn chỉ số (ADR-036 D1/D2) — {@code bonusAtk} vào ATK (đòn thường), {@code bonusDef} vào DEF
 * (chống đòn thường), {@code bonusRes} vào RES (chống skill), {@code bonusHp} vào MÁU TỐI ĐA. Cả
 * bốn đã tính SẴN hệ số cường hoá của từng món ({@link EnhancementTable#enhancedBonus}) — nơi nhận
 * ({@code WorldFacade#applyEquipmentBonus}) chỉ việc cộng thẳng, không biết gì về cường hoá.
 *
 * <p>{@code bonusHp} cộng vào {@code CombatConstants.CHARACTER_MAX_HP} — hằng số đó vẫn là
 * placeholder (GDD chưa chốt công thức HP tối đa); chủ dự án đã chấp nhận {@code maxHp = 100 +
 * bonusHp} như placeholder mới khi duyệt ADR-036, xem Consequences của ADR đó.
 */
public record EquipmentBonus(int bonusAtk, int bonusDef, int bonusRes, int bonusHp) {

    public static final EquipmentBonus NONE = new EquipmentBonus(0, 0, 0, 0);
}
