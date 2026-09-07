package dev.kage.inventory.domain;

/**
 * Một dòng catalog vật phẩm — TOÀN BỘ 10 cột của {@code item_template} (V4/V5/V6/V22), khác
 * {@link ItemTemplate} vốn cố ý chỉ mang phần "mặc đồ"/"tính chỉ số".
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
 *
 * <p>Bốn chỉ số + {@code requiredLevel} có mặt ở đây để CLIENT hiện được ô chi tiết vật phẩm
 * ("+10 DEF, cần Cấp 5") mà không phải đoán — ADR-036 D5 thêm chúng vào
 * {@code ItemCatalogEntry} của {@code inventory.proto} theo đúng luật additive của NETWORK.md §6.
 *
 * <p>{@code useEffect}/{@code useValue} thêm ở ADR-046 D5, cùng lý do và cùng luật: không có
 * chúng, client phải bày nút "Dùng" với MỌI món rồi chờ server từ chối, và ô chi tiết không nói
 * được "hồi 50 máu". {@code null} = món không dùng được.
 */
public record ItemCatalogEntry(
        long id,
        String code,
        String nameKey,
        int maxStack,
        EquipmentSlotType equipSlot,
        int bonusAtk,
        int bonusDef,
        int bonusRes,
        int bonusHp,
        int requiredLevel,
        ItemUseEffect useEffect,
        int useValue) {

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
        if (requiredLevel < 1) {
            throw new IllegalArgumentException("requiredLevel phải >= 1: " + requiredLevel);
        }
        if (useEffect != null && useValue <= 0) {
            throw new IllegalArgumentException("item dùng được phải có useValue > 0: " + useValue);
        }
    }
}
