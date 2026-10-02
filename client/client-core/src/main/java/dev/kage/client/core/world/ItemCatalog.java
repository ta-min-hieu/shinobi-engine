package dev.kage.client.core.world;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ItemCatalogResponse;
import dev.kage.protocol.ItemUseEffect;

/**
 * Bản sao catalog vật phẩm phía client (ADR-035, opcode 509/510) — tra {@code item_template_id} ra
 * {@code name_key}, {@code code}, ô trang bị và chỉ số cộng thêm.
 *
 * <p><b>Vì sao phải có.</b> Gần như mọi gói nói về vật phẩm chỉ mang một con số:
 * {@code InventoryItemView.item_template_id}, {@code ShopListingView.item_template_id},
 * {@code MailAttachmentView}. Không có catalog thì mọi giao diện đụng vật phẩm chỉ hiện được
 * "Vật phẩm #7" — đúng thứ hòm thư đang phải làm, và đúng khoảng trống ADR-035 sinh ra để lấp.
 *
 * <p><b>Server phán quyết {@code version}</b> (phương án A′ của ADR-035): client chỉ lưu lại chuỗi
 * server gửi và gửi trả nó ở lần hỏi sau. {@code unchanged=true} ⇒ giữ nguyên bản đang có, danh sách
 * {@code entries} khi đó RỖNG và không được hiểu là "catalog rỗng".
 *
 * <p>Thuần Java, không libGDX — chạy thẳng trong JUnit như {@code MailController}.
 */
final class ItemCatalog {

    /** Bản chưa có gì: gửi chuỗi rỗng là "tôi chưa cache gì cả", server sẽ trả toàn bộ. */
    static final String NO_VERSION = "";

    private final Map<Long, ItemCatalogEntry> byId = new HashMap<>();
    private final Map<String, ItemCatalogEntry> byCode = new HashMap<>();

    private String version = NO_VERSION;

    /**
     * Nhận một {@code ItemCatalogResponse}.
     *
     * @return {@code true} nếu nội dung catalog vừa THAY ĐỔI (chỗ gọi cần vẽ lại), {@code false} khi
     *     server trả {@code unchanged}
     */
    boolean adopt(ItemCatalogResponse response) {
        if (response.getUnchanged()) {
            return false;
        }
        // Thay HẲN, không cộng dồn — ADR-035 nói rõ entries là TOÀN BỘ catalog mới.
        byId.clear();
        byCode.clear();
        for (ItemCatalogEntry entry : response.getEntriesList()) {
            byId.put(entry.getId(), entry);
            byCode.put(entry.getCode(), entry);
        }
        version = response.getVersion();
        return true;
    }

    /** Chuỗi version đang cache — gửi kèm {@code GetItemCatalogRequest} lần hỏi sau. */
    String knownVersion() {
        return version;
    }

    /** Chưa nhận được catalog nào — giao diện phải hiện dạng số thay vì bịa tên. */
    boolean isEmpty() {
        return byId.isEmpty();
    }

    Optional<ItemCatalogEntry> byId(long itemTemplateId) {
        return Optional.ofNullable(byId.get(itemTemplateId));
    }

    Optional<ItemCatalogEntry> byCode(String itemCode) {
        return Optional.ofNullable(byCode.get(itemCode));
    }

    /**
     * Khoá i18n của tên vật phẩm.
     *
     * @return {@link Optional#empty()} khi chưa có catalog hoặc id lạ — chỗ gọi PHẢI tự lo hiển thị
     *     dạng số, đừng bịa ra một cái tên nghe hợp lý (Điều 24: client không được đoán thay server)
     */
    Optional<String> nameKey(long itemTemplateId) {
        return byId(itemTemplateId).map(ItemCatalogEntry::getNameKey);
    }

    /**
     * Ô trang bị của một vật phẩm; {@code UNSPECIFIED} nghĩa là KHÔNG mặc được (equip_slot NULL).
     *
     * <p>Ô mà bản {@code .proto} của client này chưa biết ({@code UNRECOGNIZED} — server mới hơn
     * thêm ô) cũng trả {@code UNSPECIFIED}: client không có tên để hiện, không có hàng để đặt, và
     * quan trọng hơn {@code EquipItemCommand.Builder.setSlot(UNRECOGNIZED)} NÉM
     * {@code IllegalArgumentException} — trên luồng render là sập ứng dụng.
     */
    EquipmentSlot equipSlot(long itemTemplateId) {
        EquipmentSlot slot = byId(itemTemplateId)
                .map(ItemCatalogEntry::getEquipSlot)
                .orElse(EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED);
        return slot == EquipmentSlot.UNRECOGNIZED ? EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED : slot;
    }

    /**
     * Hiệu ứng khi DÙNG vật phẩm (ADR-046 D5); {@code UNSPECIFIED} nghĩa là KHÔNG dùng được — cả
     * khi món thật sự không dùng được lẫn khi chưa có catalog. Cả hai đều dẫn tới cùng một hành vi
     * đúng: không bày nút "Dùng" (Điều 24 — chưa biết thì không đoán).
     */
    ItemUseEffect useEffect(long itemTemplateId) {
        return byId(itemTemplateId)
                .map(ItemCatalogEntry::getUseEffect)
                .orElse(ItemUseEffect.ITEM_USE_EFFECT_UNSPECIFIED);
    }

    /** Độ lớn hiệu ứng dùng — với {@code HEAL_HP} là số máu hồi. 0 nếu món không dùng được. */
    int useValue(long itemTemplateId) {
        return byId(itemTemplateId).map(ItemCatalogEntry::getUseValue).orElse(0);
    }

    /** Toàn bộ catalog, để giao diện cửa hàng/túi đồ duyệt. Thứ tự KHÔNG bảo đảm. */
    List<ItemCatalogEntry> entries() {
        return List.copyOf(byId.values());
    }
}
