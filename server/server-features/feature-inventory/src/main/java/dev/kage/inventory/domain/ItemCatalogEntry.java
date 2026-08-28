package dev.kage.inventory.domain;

/**
 * Một dòng catalog vật phẩm — TOÀN BỘ 6 cột của {@code item_template} (V4/V5/V6), khác
 * {@link ItemTemplate} vốn cố ý chỉ mang phần "mặc đồ"/"tính chỉ số" ({@code equipSlot},
 * {@code bonusAtk}).
 *
 * <p>Hai record cùng đọc một bảng nhưng phục vụ hai việc khác hẳn nhau: {@link ItemTemplate} là
 * dữ liệu 1 item mà UseCase gameplay cần lúc chạy; record này là dữ liệu TRA CỨU gửi trọn gói cho
 * client một lần rồi cache (ADR-035 Decision-2, phương án A′). Gộp làm một sẽ bắt mọi UseCase
 * gameplay chở theo {@code nameKey}/{@code maxStack} mà chúng không dùng.
 *
 * <p>KHÔNG mang tên hiển thị và KHÔNG mang icon: {@code nameKey} là KHOÁ i18n (client tra trong
 * bundle của nó), icon tra theo {@code code} trong atlas (ADR-028). Server chỉ gửi thứ server sở hữu.
 *
 * <p>{@code equipSlot == null} nghĩa là item KHÔNG mặc được (khớp {@code equip_slot} NULL trong DB).
 */
public record ItemCatalogEntry(
        long id, String code, String nameKey, int maxStack, EquipmentSlotType equipSlot, int bonusAtk) {

    public ItemCatalogEntry {
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("code không được rỗng");
        }
        if (nameKey == null || nameKey.isBlank()) {
            throw new IllegalArgumentException("nameKey không được rỗng");
        }
        if (maxStack <= 0) {
            throw new IllegalArgumentException("maxStack phải > 0: " + maxStack);
        }
    }
}
