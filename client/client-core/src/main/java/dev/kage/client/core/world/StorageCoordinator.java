package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.DepositItemCommand;
import dev.kage.protocol.DepositItemResponse;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListStorageRequest;
import dev.kage.protocol.ListStorageResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.StorageOutcome;
import dev.kage.protocol.StoredItemView;
import dev.kage.protocol.WithdrawItemCommand;
import dev.kage.protocol.WithdrawItemResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Kho đồ tông môn (ADR-061) — MỘT cửa sổ {@link ReportPanel} hai phần: đồ trong kho (nút "Rút") rồi đồ
 * trong túi (nút "Gửi"). Gửi/rút luôn CẢ chồng (D4).
 *
 * <p><b>Vì sao không đặt nút "Gửi" trong túi đồ.</b> Mọi cửa sổ mở bằng phím dùng chung cổng
 * {@code PanelHub#noneOpen}: đang mở kho thì phím I không mở túi được, và hai khung giữa màn hình chồng
 * lên nhau. Tự hiện danh sách túi ở đây tránh cả hai — {@code PanelHub#dispatch} hỏi MỌI luồng, nên lớp
 * này đọc chung {@code ListInventoryResponse} với túi đồ mà hai bên không cần biết nhau.
 *
 * <p><b>Không có phím mở.</b> Kho là một NƠI: server tự gửi {@code ListStorageResponse} khi người chơi
 * chọn "nhờ sư tỷ giữ đồ" trong hội thoại Tô Vân Nhi. Đi xa rồi bấm thì server trả {@code TOO_FAR}.
 */
final class StorageCoordinator implements WindowFlow {

    private static final Map<StorageOutcome, String> OUTCOME_KEYS = new EnumMap<>(StorageOutcome.class);

    static {
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_OK, "storage.status.ok");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_NOT_FOUND, "storage.error.not_found");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_INVALID_QUANTITY, "storage.error.not_found");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_EQUIPPED, "storage.error.equipped");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_ITEM_IN_TRADE, "storage.error.in_trade");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_STORAGE_FULL, "storage.error.storage_full");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_BAG_FULL, "storage.error.bag_full");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_TOO_FAR, "storage.error.too_far");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_SOCKETED, "storage.error.socketed");
        OUTCOME_KEYS.put(StorageOutcome.STORAGE_OUTCOME_LOCKED, "storage.error.locked");
    }

    /** Một dòng có nút: rút một dòng kho, hoặc gửi một dòng túi. */
    private record Action(boolean withdraw, long id, int quantity) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    private final List<StoredItemView> stored = new ArrayList<>();
    private final List<InventoryItemView> bag = new ArrayList<>();
    private int storageCapacity;

    /** Hành động của từng dòng panel, cùng chỉ số với {@link ReportPanel#setRows}; dòng tiêu đề = rỗng. */
    private final List<Action> actions = new ArrayList<>();

    StorageCoordinator(ReportPanel panel, WorldCommandSender sender, ItemCatalogCoordinator itemCatalog) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = itemCatalog;
        panel.setCanOpen(() -> false);
        panel.setOnRefresh(this::request);
        panel.setOnRowAction(this::onRowAction);
        panel.setStatus(text("storage.hint.open"));
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_LIST_STORAGE_REQUEST_VALUE, ListStorageRequest.getDefaultInstance());
        sender.listInventory();
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        Action action = actions.get(rowIndex);
        if (action.withdraw()) {
            sender.sendReadRequest(
                    Opcode.OPCODE_WITHDRAW_ITEM_COMMAND_VALUE,
                    WithdrawItemCommand.newBuilder()
                            .setStoredItemId(action.id())
                            .setQuantity(action.quantity())
                            .build());
        } else {
            sender.sendReadRequest(
                    Opcode.OPCODE_DEPOSIT_ITEM_COMMAND_VALUE,
                    DepositItemCommand.newBuilder()
                            .setInventoryItemId(action.id())
                            .setQuantity(action.quantity())
                            .build());
        }
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
        int opcode = frame.header().opcode();
        if (opcode == Opcode.OPCODE_LIST_STORAGE_RESPONSE_VALUE) {
            ListStorageResponse response = ListStorageResponse.parseFrom(frame.payload());
            replaceStorage(response.getItemsList(), response.getCapacity());
            panel.setStatus(text("storage.hint.open"));
            if (!panel.isOpen()) {
                panel.show();
                sender.listInventory(); // túi có thể đã đổi từ lần hỏi trước — phần "Gửi" phải đúng
            }
            redraw();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            onBag(ListInventoryResponse.parseFrom(frame.payload()));
            return false; // KHÔNG nuốt: túi đồ cũng cần gói này
        }
        if (opcode == Opcode.OPCODE_DEPOSIT_ITEM_RESPONSE_VALUE) {
            DepositItemResponse response = DepositItemResponse.parseFrom(frame.payload());
            settle(response.getOutcome(), response.getItemsList(), response.getCapacity());
            return true;
        }
        if (opcode == Opcode.OPCODE_WITHDRAW_ITEM_RESPONSE_VALUE) {
            WithdrawItemResponse response = WithdrawItemResponse.parseFrom(frame.payload());
            settle(response.getOutcome(), response.getItemsList(), response.getCapacity());
            return true;
        }
        return false;
    }

    /** Túi đồ mới — chỉ giữ món KHÔNG đang mặc (đang mặc thì server từ chối gửi, D4). */
    private void onBag(ListInventoryResponse response) {
        Set<Long> worn = new HashSet<>();
        for (EquippedSlotView slot : response.getEquippedList()) {
            worn.add(slot.getItemId());
        }
        bag.clear();
        for (InventoryItemView item : response.getItemsList()) {
            if (!worn.contains(item.getItemId())) {
                bag.add(item);
            }
        }
        if (panel.isOpen()) {
            redraw();
        }
    }

    private void settle(StorageOutcome outcome, List<StoredItemView> items, int capacity) {
        panel.setStatus(text(OUTCOME_KEYS.getOrDefault(outcome, "storage.error.unknown")));
        if (outcome == StorageOutcome.STORAGE_OUTCOME_TOO_FAR) {
            return; // response TOO_FAR không mang kho — giữ nguyên danh sách đang hiện
        }
        replaceStorage(items, capacity);
        redraw();
        if (outcome == StorageOutcome.STORAGE_OUTCOME_OK) {
            sender.listInventory(); // túi vừa đổi; cả túi đồ lẫn phần "Gửi" ở đây cùng nhận gói trả lời
        }
    }

    private void replaceStorage(List<StoredItemView> items, int capacity) {
        stored.clear();
        stored.addAll(items);
        storageCapacity = capacity;
    }

    /** Dựng lại cả hai phần — chỉ khi server vừa trả lời, không mỗi khung. */
    private void redraw() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        rows.add(new ReportPanel.Row(
                KageI18n.formatOrKey(i18n, "storage.section.stored", stored.size(), storageCapacity), true));
        actions.add(null);
        for (StoredItemView item : stored) {
            rows.add(new ReportPanel.Row(
                    describe(item.getItemTemplateId(), item.getEnhanceLevel(), item.getQuantity()),
                    false,
                    "storage.action.withdraw"));
            actions.add(new Action(true, item.getStoredItemId(), item.getQuantity()));
        }
        rows.add(new ReportPanel.Row(text("storage.section.bag"), true));
        actions.add(null);
        for (InventoryItemView item : bag) {
            rows.add(new ReportPanel.Row(
                    describe(item.getItemTemplateId(), item.getEnhanceLevel(), item.getQuantity()),
                    false,
                    "storage.action.deposit"));
            actions.add(new Action(false, item.getItemId(), item.getQuantity()));
        }
        panel.setRows(rows);
    }

    /** {@code Tên +cấp xSố} — tên tra catalog; chưa có catalog thì hiện dạng số, không bịa (Điều 24). */
    private String describe(long itemTemplateId, int enhanceLevel, int quantity) {
        String name = itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
        String enhance = enhanceLevel > 0 ? " +" + enhanceLevel : "";
        String count = quantity > 1 ? " x" + quantity : "";
        return name + enhance + count;
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    @Override
    public void reset() {
        panel.close();
        stored.clear();
        bag.clear();
        actions.clear();
        panel.setRows(List.of());
        panel.setStatus(text("storage.hint.open"));
    }
}
