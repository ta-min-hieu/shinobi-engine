package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.LongConsumer;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.InventoryPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.EquipItemResponse;
import dev.kage.protocol.EquipmentSlot;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.UnequipItemResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link InventoryController} (trạng thái), {@link InventoryPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành túi đồ + trang bị chạy được (ADR-035, opcode 501-508) —
 * cùng hình dạng {@link MailCoordinator}.
 *
 * <p>Tra vào {@link ItemCatalogCoordinator} để hiện TÊN + Ô TRANG BỊ của mỗi item — chưa có catalog
 * thì hiện dạng số ("Vật phẩm #7"), KHÔNG bịa (Điều 24, xem javadoc {@link ItemCatalog}), và vì lý
 * do tương tự nút Mặc cũng ẨN khi chưa có catalog: không biết item có mặc được không thì không đoán.
 * Tra vào {@link GoldCoordinator} để hiện số vàng cạnh tiêu đề — túi đồ là chỗ ĐẦU TIÊN dùng tới nó.
 *
 * <p><b>Luật một dòng: hỏi lại server sau MỌI lệnh đổi trạng thái</b> (xem {@link #settle}), cùng
 * {@link MailCoordinator}/{@link GuildCoordinator}: client không tự cộng/trừ item hay đổi map trang
 * bị theo suy đoán, kể cả khi lệnh vừa gửi trả OK.
 */
final class InventoryCoordinator implements WindowFlow {

    /**
     * Gộp 2 phụ thuộc "dữ liệu nền" (không phải mạng, không phải panel) để constructor không vượt
     * ngưỡng {@code ExcessiveParameterList} của PMD (config/pmd, minimum=5) — cùng lý do
     * {@link PlayContext} gộp {@code EcsWorld}+{@code InputMapper}.
     */
    record DataSources(ItemCatalogCoordinator itemCatalog, GoldCoordinator gold) {}

    /** 7 slot trang bị THẬT — loại {@code EQUIPMENT_SLOT_UNSPECIFIED} (sentinel, không dùng cho lệnh thật). */
    private static final List<EquipmentSlot> REAL_SLOTS = List.of(
            EquipmentSlot.EQUIPMENT_SLOT_WEAPON,
            EquipmentSlot.EQUIPMENT_SLOT_ARMOR,
            EquipmentSlot.EQUIPMENT_SLOT_HELMET,
            EquipmentSlot.EQUIPMENT_SLOT_GLOVE,
            EquipmentSlot.EQUIPMENT_SLOT_BOOT,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_1,
            EquipmentSlot.EQUIPMENT_SLOT_ACCESSORY_2);

    private final InventoryController controller = new InventoryController();
    private final InventoryPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final GoldCoordinator gold;
    private final I18NBundle i18n = KageI18n.load();

    /**
     * Người chơi bấm Bán — {@code WorldScreen} nối vào {@code ShopCoordinator}. Túi đồ KHÔNG tự gửi
     * {@code SellItemCommand}: khoá idempotency của lệnh Bán (ADR-025) do {@code ShopController}
     * giữ, và một lệnh phải có ĐÚNG một chủ. Túi đồ chỉ biết {@code inventory_item.id} — thứ mà
     * cửa hàng cần và không có cách nào tự lấy.
     */
    private LongConsumer onSellRequested = itemId -> {};

    /** Đệm dựng sẵn cho {@link InventoryPanel#setItems} — chỉ đổi khi server vừa trả lời. */
    private final List<InventoryPanel.ItemRow> itemRows = new ArrayList<>();

    /** Đệm dựng sẵn cho {@link InventoryPanel#setEquipment} — LUÔN đúng 7 dòng. */
    private final List<InventoryPanel.EquipmentRow> equipmentRows = new ArrayList<>();

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím I — cổng phím CHUNG của mọi cửa sổ, xem
     *     {@link InventoryPanel#setCanOpen}.
     */
    InventoryCoordinator(
            InventoryPanel panel, WorldCommandSender sender, DataSources dataSources, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = dataSources.itemCatalog();
        this.gold = dataSources.gold();
        panel.setCanOpen(canOpen);
        panel.setOnRefresh(this::onOpened);
        panel.setOnSelect(this::onSelect);
        panel.setOnEquipSelected(this::onEquipSelected);
        panel.setOnSellSelected(this::onSellSelected);
        panel.setOnUnequip(this::onUnequip);
        panel.setStatus(text("inventory.hint.open"));
        panel.setGold(goldText());
        itemCatalog.addCatalogListener(this::refreshView);
        gold.addGoldListener(amount -> panel.setGold(goldText()));
        refreshView();
    }

    /** Xem {@link #onSellRequested} — {@code WorldScreen} nối lời gọi này sang cửa hàng. */
    void setOnSellRequested(LongConsumer listener) {
        this.onSellRequested = listener;
    }

    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    @Override
    public void close() {
        panel.close();
    }

    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            controller.onInventory(ListInventoryResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_EQUIP_ITEM_RESPONSE_VALUE) {
            onEquipSettled(EquipItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_UNEQUIP_ITEM_RESPONSE_VALUE) {
            onUnequipSettled(UnequipItemResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE) {
            onSellSettled(SellItemResponse.parseFrom(payload));
            return true;
        }
        return false;
    }

    /**
     * Kết quả Mặc. {@code EquipItemResponse} KHÔNG có enum outcome (khác {@code ClaimMailOutcome}) —
     * chỉ {@code bool success} + {@code error_key} tự do đã LÀ khoá i18n (NETWORK.md §107), xem
     * javadoc {@link InventoryController}.
     */
    private void onEquipSettled(EquipItemResponse response) {
        settle(
                response.getSuccess()
                        ? "inventory.notice.equipped"
                        : InventoryController.errorMessageKey(response.getErrorKey())
                                .orElse("inventory.error.unknown"));
    }

    private void onUnequipSettled(UnequipItemResponse response) {
        settle(
                response.getSuccess()
                        ? "inventory.notice.unequipped"
                        : InventoryController.errorMessageKey(response.getErrorKey())
                                .orElse("inventory.error.unknown"));
    }

    /**
     * Kết quả Bán. {@code ShopCoordinator} cũng nhận gói này và nói kết quả ra khung cửa hàng —
     * {@link PanelHub#dispatch} hỏi MỌI luồng nên không ai giấu gói của ai. Nói lại ở đây là CẦN,
     * không phải thừa: người chơi bấm Bán từ túi đồ thì khung cửa hàng thường đang đóng.
     *
     * <p>Bảng khoá i18n mượn thẳng của {@link ShopController} thay vì chép lại: cùng một
     * {@code SellOutcome} phải đọc ra cùng một câu, dù bấm Bán từ đâu.
     */
    private void onSellSettled(SellItemResponse response) {
        settle(ShopController.sellMessageKey(response.getOutcome()).orElse("shop.error.unknown"));
    }

    /**
     * Bấm Bán trên item đang chọn. Bán ĐÚNG MỘT món mỗi lần bấm (xem {@code inventory.action.sell})
     * — túi đồ không có ô nhập số lượng, và bán nhầm cả chồng đồ là thứ không hoàn tác được.
     */
    private void onSellSelected() {
        controller.selected().ifPresent(item -> onSellRequested.accept(item.itemId()));
    }

    /** Nói ra kết quả một lệnh, rồi hỏi lại server — cùng {@link MailCoordinator#settle}. */
    private void settle(String messageKey) {
        panel.setStatus(text(messageKey));
        sender.listInventory();
    }

    /** Người chơi vừa mở cửa sổ (hoặc bấm Làm mới) — hỏi lại server, nguồn chân lý duy nhất. */
    private void onOpened() {
        sender.listInventory();
    }

    private void onSelect(long itemId) {
        controller.select(itemId);
        refreshDetail();
    }

    /**
     * Bấm Mặc trên item ĐANG CHỌN. Slot suy từ catalog, không phải người chơi chọn tay — mỗi item
     * mặc được đúng MỘT slot cố định (item_template.equip_slot).
     */
    private void onEquipSelected() {
        controller.selected().ifPresent(item -> {
            EquipmentSlot slot = itemCatalog.equipSlot(item.itemTemplateId());
            if (slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
                // Nút không đáng lẽ hiện ra lúc này (xem refreshDetail) — phòng hờ trạng thái vừa đổi
                // giữa lúc panel vẽ và lúc người chơi bấm (catalog vừa mất do reconnect, chẳng hạn).
                return;
            }
            sender.equipItem(item.itemId(), slot);
        });
    }

    private void onUnequip(int slotNumber) {
        EquipmentSlot slot = EquipmentSlot.forNumber(slotNumber);
        if (slot == null || slot == EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED) {
            return; // số lạ — panel chỉ vọng lại đúng số nó nhận từ refreshEquipment, không nên xảy ra.
        }
        sender.unequipItem(slot);
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống panel. Gọi sau MỖI response tra cứu, không phải mỗi khung. */
    private void refreshView() {
        List<InventoryController.Item> all = controller.items();
        int shown = Math.min(all.size(), InventoryController.MAX_VISIBLE_SLOTS);
        itemRows.clear();
        for (int index = 0; index < shown; index++) {
            InventoryController.Item item = all.get(index);
            itemRows.add(new InventoryPanel.ItemRow(item.itemId(), iconCodeOf(item), itemDisplayText(item)));
        }
        panel.setItems(itemRows, capacityText(all.size()), listNotice(all.size(), all.size() - shown));
        refreshEquipment();
        refreshDetail();
    }

    private void refreshEquipment() {
        equipmentRows.clear();
        for (EquipmentSlot slot : REAL_SLOTS) {
            long equippedItemId = controller.equippedItemId(slot);
            String displayText = equippedItemId == 0L
                    ? text("inventory.slot.empty")
                    : controller
                            .byId(equippedItemId)
                            .map(this::itemDisplayText)
                            .orElseGet(() -> format("inventory.label.raw_item", equippedItemId));
            equipmentRows.add(new InventoryPanel.EquipmentRow(
                    slot.getNumber(), text(slotNameKey(slot)), displayText, equippedItemId != 0L));
        }
        panel.setEquipment(equipmentRows);
    }

    /**
     * Chi tiết item đang chọn + có bày nút Mặc hay không.
     *
     * <p>Nút Bán bày ra với MỌI item đang chọn — bán được hay không là việc của server
     * ({@code SELL_OUTCOME_ITEM_NOT_SELLABLE}), client không có bảng giá bán để đoán trước.
     *
     * <p>ẨN nút Mặc khi chưa có catalog (Điều 24: không biết thì không đoán) VÀ khi item đang chọn ĐÃ
     * đang mặc đúng slot đó — bày ra lúc đó là bày một nút chỉ để mặc lại chính nó, vô nghĩa với
     * người chơi dù server vẫn xử lý được (OK, không hỏng).
     */
    private void refreshDetail() {
        controller
                .selected()
                .ifPresentOrElse(
                        item -> {
                            EquipmentSlot slot = itemCatalog.equipSlot(item.itemTemplateId());
                            boolean equippable = slot != EquipmentSlot.EQUIPMENT_SLOT_UNSPECIFIED;
                            boolean alreadyEquipped = equippable && controller.equippedItemId(slot) == item.itemId();
                            panel.showDetail(itemDisplayText(item), equippable && !alreadyEquipped, true);
                        },
                        () -> panel.showDetail(text("inventory.detail.none"), false, false));
    }

    /** Tên + số lượng + mức cường hoá của một item, ghép sẵn thành một chuỗi hiển thị. */
    private String itemDisplayText(InventoryController.Item item) {
        String name = itemCatalog
                .nameKey(item.itemTemplateId())
                .map(this::text)
                .orElseGet(() -> format("inventory.label.raw_item", item.itemTemplateId()));
        StringBuilder builder = new StringBuilder(name);
        if (item.quantity() > 1) {
            builder.append(' ').append(format("inventory.label.quantity", item.quantity()));
        }
        if (item.enhanceLevel() > 0) {
            builder.append(' ').append(format("inventory.label.enhance", item.enhanceLevel()));
        }
        return builder.toString();
    }

    /** {@code item_template.code} để tra icon (ADR-028) — rỗng khi chưa có catalog, panel để trống. */
    private String iconCodeOf(InventoryController.Item item) {
        return itemCatalog
                .byId(item.itemTemplateId())
                .map(ItemCatalogEntry::getCode)
                .orElse("");
    }

    private String capacityText(int itemCount) {
        return format2("inventory.label.capacity", itemCount, controller.capacity());
    }

    /**
     * Câu nói thêm dưới danh sách — cùng {@link MailCoordinator#listNotice}.
     */
    private String listNotice(int total, int hidden) {
        if (total == 0) {
            return text("inventory.empty");
        }
        return hidden <= 0 ? "" : format("inventory.list.truncated", hidden);
    }

    private static String slotNameKey(EquipmentSlot slot) {
        return switch (slot) {
            case EQUIPMENT_SLOT_WEAPON -> "equipment.slot.weapon";
            case EQUIPMENT_SLOT_ARMOR -> "equipment.slot.armor";
            case EQUIPMENT_SLOT_HELMET -> "equipment.slot.helmet";
            case EQUIPMENT_SLOT_GLOVE -> "equipment.slot.glove";
            case EQUIPMENT_SLOT_BOOT -> "equipment.slot.boot";
            case EQUIPMENT_SLOT_ACCESSORY_1 -> "equipment.slot.accessory_1";
            case EQUIPMENT_SLOT_ACCESSORY_2 -> "equipment.slot.accessory_2";
            // UNSPECIFIED/UNRECOGNIZED không xuất hiện ở đây — REAL_SLOTS đã lọc trước.
            default -> "inventory.error.unknown";
        };
    }

    private String goldText() {
        return gold.hasAmount() ? format("inventory.label.gold", gold.amount()) : text("inventory.label.gold_unknown");
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    private String format(String key, Object argument) {
        try {
            return i18n.format(key, argument);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    private String format2(String key, Object first, Object second) {
        try {
            return i18n.format(key, first, second);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    /** Rời màn hình/mất kết nối — túi của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("inventory.hint.open"));
        refreshView();
    }
}
