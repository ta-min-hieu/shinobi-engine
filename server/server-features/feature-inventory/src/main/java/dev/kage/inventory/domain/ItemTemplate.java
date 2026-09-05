package dev.kage.inventory.domain;

/**
 * Dữ liệu TĨNH của 1 loại item (DATABASE.md §2.2 {@code item_template}) — chỉ mang những gì
 * "mặc đồ"/"tính lại chỉ số dẫn xuất"/"xếp chồng" cần. KHÔNG mang {@code nameKey}: chưa UseCase
 * nào đọc. Bản TRA CỨU đầy đủ cho client là {@link ItemCatalogEntry}, không phải record này.
 *
 * <p>{@code maxStack} thêm 2026-09-05. Trước đó record này cố ý bỏ nó với lý do "chưa có UseCase
 * nào cần đọc" — và hệ quả là {@code Inventory.add()} KHÔNG BAO GIỜ gộp chồng: 17 phi đao nằm
 * trong 5 ô túi khác nhau dù {@code max_stack = 99}. Cột trong DB đã đúng từ đầu (mọi thứ có
 * {@code equipSlot} đều là 1, tiêu hao/nguyên liệu là 99), chỉ là code không đọc.
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
        int maxStack,
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
        if (maxStack < 1) {
            throw new IllegalArgumentException("maxStack phải >= 1: " + maxStack);
        }
        if (equipSlot != null && maxStack != 1) {
            // Luật của chủ dự án: trang bị KHÔNG xếp chồng, tiêu hao/nhiệm vụ/sự kiện thì có.
            // Dữ liệu hiện tại đã đúng luật này; chặn ở đây để nó không trôi đi.
            throw new IllegalArgumentException("item mặc được phải có maxStack = 1: " + maxStack);
        }
    }

    public boolean isEquippable() {
        return equipSlot != null;
    }

    /** Có gộp được nhiều cái vào 1 ô túi không — kéo theo từ {@link #maxStack}. */
    public boolean isStackable() {
        return maxStack > 1;
    }
}
