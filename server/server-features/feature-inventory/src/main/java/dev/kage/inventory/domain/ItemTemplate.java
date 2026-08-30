package dev.kage.inventory.domain;

/**
 * Dữ liệu TĨNH của 1 loại item (DATABASE.md §2.2 {@code item_template}) — chỉ mang những gì
 * "mặc đồ"/"tính lại chỉ số dẫn xuất" cần ({@code equipSlot}, 4 chỉ số cộng thêm,
 * {@code requiredLevel}), KHÔNG mang {@code nameKey}/{@code maxStack}: chưa có UseCase nào cần đọc
 * lại 2 trường đó (YAGNI — thêm khi có nhu cầu thật, không suy đoán trước). Bản TRA CỨU đầy đủ cho
 * client là {@link ItemCatalogEntry}, không phải record này.
 *
 * <p>{@code equipSlot == null} nghĩa là item KHÔNG mặc được (VD "kunai" — vật phẩm tiêu hao), lúc
 * đó MỌI chỉ số cộng thêm phải bằng 0 (item không mặc được thì không cộng chỉ số — validate trong
 * compact constructor).
 *
 * <p>Bốn chỉ số (ADR-036 D1) khớp đúng bốn cột {@code bonus_atk}/{@code bonus_def}/
 * {@code bonus_res}/{@code bonus_hp}. Trước ADR-036 chỉ có {@code bonusAtk}, khiến 6/7 ô trang bị
 * phải cộng ATK để không thành nội dung chết — xem Context của ADR đó. Chưa có
 * {@code requiredFaction}: chờ bộ đồ riêng hệ phái đầu tiên.
 */
public record ItemTemplate(
        long id,
        EquipmentSlotType equipSlot,
        int bonusAtk,
        int bonusDef,
        int bonusRes,
        int bonusHp,
        int requiredLevel) {

    public ItemTemplate {
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
        if (equipSlot == null && (bonusAtk != 0 || bonusDef != 0 || bonusRes != 0 || bonusHp != 0)) {
            throw new IllegalArgumentException("item không mặc được (equipSlot=null) không được có chỉ số cộng thêm");
        }
        if (requiredLevel < 1) {
            throw new IllegalArgumentException("requiredLevel phải >= 1: " + requiredLevel);
        }
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }
}
