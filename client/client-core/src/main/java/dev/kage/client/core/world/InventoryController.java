package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;

/**
 * Trạng thái túi đồ + trang bị (ADR-035, opcode 507/508) — thuần Java, không libGDX/GL, test được
 * thẳng như {@link MailController} (UI.md §4: controller giữ trạng thái, widget chỉ vẽ).
 *
 * <p>Giữ đúng bốn thứ: <b>danh sách vật phẩm</b> server vừa trả về, <b>sức chứa</b> túi, <b>slot nào
 * đang mặc item nào</b>, và <b>vật phẩm đang chọn</b> để xem chi tiết/mặc.
 *
 * <p><b>Item đang mặc VẪN nằm trong {@code items}.</b> {@code equipment_slot.inventory_item_id}
 * (V5__add_equipment.sql) tham chiếu tới {@code inventory_item} chứ KHÔNG xoá dòng đó khi mặc, nên
 * tra tên một item đang mặc là tra thẳng {@link #byId(long)} bằng {@code item_id} lấy từ
 * {@link #equippedItemId(EquipmentSlot)} — không cần packet nào khác ngoài {@code ListInventoryResponse}.
 *
 * <p><b>Server là nguồn chân lý duy nhất.</b> Lớp này KHÔNG tự sửa danh sách sau khi mặc/gỡ —
 * {@link InventoryCoordinator} luôn hỏi lại {@code ListInventoryRequest} sau MỌI lệnh đổi trạng thái
 * (Điều 15), cùng nguyên tắc {@link MailController}/{@link GuildController}.
 *
 * <p><b>⚠️ Khác Mail: KHÔNG có enum outcome thật.</b> {@code EquipItemResponse}/
 * {@code UnequipItemResponse} chỉ có {@code bool success} + {@code string error_key} tự do
 * (NETWORK.md §107 — error_key ĐÃ LÀ khoá i18n do server chọn sẵn, không phải một mã cần tra bảng).
 * {@link #errorMessageKey(String)} vì vậy không tra một enum như
 * {@code MailController.claimMessageKey}, mà đối chiếu với đúng tập ba chuỗi THẬT SỰ server phát ra
 * hôm nay ({@code GatewayChannelHandler#toErrorKey}, {@code #handleUnequipItemCommandBlocking}) —
 * đóng vai trò tương đương một bảng tra outcome, và {@code InventoryControllerTest} gác đúng ba
 * chuỗi này thay vì gác một enum không tồn tại.
 */
final class InventoryController {

    /**
     * Số dòng túi hiển thị cùng lúc. Trần cứng cùng lý do {@code MailController.MAX_VISIBLE_ROWS}:
     * {@code KageSkin} chưa có style {@code ScrollPane} nào, nên danh sách dài hơn sẽ đội khung cao
     * vượt khỏi màn hình chứ không tự cuộn.
     */
    static final int MAX_VISIBLE_SLOTS = 10;

    /**
     * Ba {@code error_key} THẬT server phát cho Equip/Unequip hôm nay — xem javadoc lớp về việc
     * inventory.proto không có enum outcome cho hai lệnh này.
     */
    private static final Set<String> KNOWN_ERROR_KEYS =
            Set.of("error.item_not_found", "error.wrong_equipment_slot", "error.slot_not_equipped");

    /**
     * Một dòng túi đồ.
     *
     * @param itemId {@code inventory_item.id} THẬT — gửi thẳng lại server ở {@code EquipItemCommand}.
     * @param itemTemplateId khoá của {@code item_template}, tra catalog (ADR-035) ra tên/slot/icon.
     * @param slotIndex vị trí trong túi — {@link InventoryController} sắp theo trường này.
     */
    record Item(long itemId, long itemTemplateId, int quantity, int enhanceLevel, int slotIndex) {}

    private final List<Item> items = new ArrayList<>();
    private int capacity;
    private final Map<EquipmentSlot, Long> equipped = new EnumMap<>(EquipmentSlot.class);

    /** Item đang xem chi tiết trong khung, 0 = chưa chọn cái nào. */
    private long selectedItemId;

    /**
     * Ảnh chụp túi mới từ server — THAY hoàn toàn, không trộn với bản cũ, cùng luật
     * {@link MailController#onMailList}.
     */
    void onInventory(ListInventoryResponse response) {
        items.clear();
        for (InventoryItemView view : response.getItemsList()) {
            items.add(new Item(
                    view.getItemId(),
                    view.getItemTemplateId(),
                    view.getQuantity(),
                    view.getEnhanceLevel(),
                    view.getSlotIndex()));
        }
        items.sort(Comparator.comparingInt(Item::slotIndex));
        capacity = response.getCapacity();
        equipped.clear();
        for (EquippedSlotView view : response.getEquippedList()) {
            equipped.put(view.getSlot(), view.getItemId());
        }
        if (byId(selectedItemId).isEmpty()) {
            selectedItemId = 0L;
        }
    }

    /** Vật phẩm trong túi, đã sắp theo {@code slotIndex}. */
    List<Item> items() {
        return List.copyOf(items);
    }

    /** Sức chứa túi — hằng số phía server (V4), gửi kèm để client vẽ đủ ô trống mà không phải đoán. */
    int capacity() {
        return capacity;
    }

    /** Tra một item theo id THẬT — dùng cho CẢ túi lẫn trang bị (xem javadoc lớp). */
    Optional<Item> byId(long itemId) {
        if (itemId == 0L) {
            return Optional.empty();
        }
        for (Item item : items) {
            if (item.itemId() == itemId) {
                return Optional.of(item);
            }
        }
        return Optional.empty();
    }

    /** {@code item_id} đang mặc ở slot, 0 nếu trống — 0 không đụng hàng vì {@code inventory_item.id} bắt đầu từ 1. */
    long equippedItemId(EquipmentSlot slot) {
        return equipped.getOrDefault(slot, 0L);
    }

    /** Vật phẩm đang xem chi tiết, rỗng khi chưa chọn (hoặc vừa biến mất khỏi túi sau một lần hỏi lại). */
    Optional<Item> selected() {
        return byId(selectedItemId);
    }

    /** Chọn một item để xem chi tiết/mặc. Id lạ thì KHÔNG đổi gì — danh sách có thể đã đổi dưới chân cú bấm. */
    void select(long itemId) {
        if (byId(itemId).isPresent()) {
            selectedItemId = itemId;
        }
    }

    /**
     * Khoá i18n của một {@code error_key} do server gửi kèm response THẤT BẠI.
     *
     * @return {@link Optional#empty()} khi {@code error_key} lạ (server MỚI HƠN client, đã thêm
     *     nhánh lỗi mới) — chỗ gọi dịch nó thành {@code inventory.error.unknown} thay vì hiện thẳng
     *     một chuỗi chưa có bản dịch, cùng lý do {@link MailController#claimMessageKey}.
     */
    static Optional<String> errorMessageKey(String serverErrorKey) {
        return KNOWN_ERROR_KEYS.contains(serverErrorKey) ? Optional.of(serverErrorKey) : Optional.empty();
    }

    /** Rời màn hình/mất kết nối — túi của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        items.clear();
        capacity = 0;
        equipped.clear();
        selectedItemId = 0L;
    }
}
