package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyOrderFilledEvent;
import dev.kage.protocol.BuyOrderOutcome;
import dev.kage.protocol.BuyOrderView;
import dev.kage.protocol.CancelBuyOrderCommand;
import dev.kage.protocol.CancelBuyOrderResponse;
import dev.kage.protocol.CreateBuyOrderCommand;
import dev.kage.protocol.CreateBuyOrderResponse;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ItemCatalogEntry;
import dev.kage.protocol.ListBuyOrdersRequest;
import dev.kage.protocol.ListBuyOrdersResponse;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellToBuyOrderCommand;
import dev.kage.protocol.SellToBuyOrderResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Cửa sổ cầu mua (ADR-116) — mở bằng lệnh chat {@code /caumua}. Ba tab: lệnh đang mở của người khác (nút Bán — client
 * chọn chồng món chưa mặc, chưa cường hoá đầu tiên đúng loại; server kiểm lại hết), lệnh của mình (Huỷ), đặt lệnh (mọi
 * món trong catalog; ô nhập "số lượng x đơn giá").
 */
final class BuyOrderCoordinator implements WindowFlow {

    static final List<String> TAB_KEYS = List.of("buyorder.tab.browse", "buyorder.tab.mine", "buyorder.tab.place");

    private static final String COMMAND = "/caumua";
    private static final int TAB_BROWSE = 0;
    private static final int TAB_MINE = 1;

    /** Hành động của một dòng có nút. */
    private record Action(int tab, long id, int quantity) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator chat;
    private final I18NBundle i18n = KageI18n.load();
    private final List<BuyOrderView> orders = new ArrayList<>();
    private final List<BuyOrderView> mine = new ArrayList<>();
    private final List<InventoryItemView> sellable = new ArrayList<>();
    private final List<Action> actions = new ArrayList<>();
    private int tab = TAB_BROWSE;

    BuyOrderCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.chat = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(index -> {
            tab = index;
            redraw();
        });
        panel.setOnRowAction(this::onRowAction);
        panel.enableInput("buyorder.input");
        panel.setStatus(text("buyorder.hint"));
        chat.addSlashCommands(this::onSlash);
    }

    private boolean onSlash(String line) {
        if (!COMMAND.equals(line.strip().toLowerCase(Locale.ROOT))) {
            return false;
        }
        panel.open();
        return true;
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_LIST_BUY_ORDERS_REQUEST_VALUE, ListBuyOrdersRequest.getDefaultInstance());
        sender.listInventory();
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        Action action = actions.get(rowIndex);
        if (action.tab() == TAB_BROWSE) {
            sell(action);
        } else if (action.tab() == TAB_MINE) {
            sender.sendReadRequest(
                    Opcode.OPCODE_CANCEL_BUY_ORDER_COMMAND_VALUE,
                    CancelBuyOrderCommand.newBuilder().setOrderId(action.id()).build());
        } else {
            place(action.id());
        }
    }

    /** Bán chồng đầu tiên đúng loại — {@code action.quantity} là số lệnh còn cần. */
    private void sell(Action action) {
        long templateId = orderTemplate(action.id());
        Optional<InventoryItemView> stack = sellable.stream()
                .filter(item -> item.getItemTemplateId() == templateId)
                .findFirst();
        if (stack.isEmpty()) {
            panel.setStatus(text("buyorder.error.no_stack"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_SELL_TO_BUY_ORDER_COMMAND_VALUE,
                SellToBuyOrderCommand.newBuilder()
                        .setOrderId(action.id())
                        .setInventoryItemId(stack.get().getItemId())
                        .setQuantity(Math.min(action.quantity(), stack.get().getQuantity()))
                        .build());
    }

    private long orderTemplate(long orderId) {
        return orders.stream()
                .filter(order -> order.getOrderId() == orderId)
                .mapToLong(BuyOrderView::getItemTemplateId)
                .findFirst()
                .orElse(0L);
    }

    private void place(long itemTemplateId) {
        long[] parsed = parseQuantityAndPrice(panel.inputText());
        if (parsed.length == 0) {
            panel.setStatus(text("buyorder.error.input"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CREATE_BUY_ORDER_COMMAND_VALUE,
                CreateBuyOrderCommand.newBuilder()
                        .setItemTemplateId(itemTemplateId)
                        .setQuantity((int) parsed[0])
                        .setUnitPrice(parsed[1])
                        .build());
    }

    /** "5x100", "5 x 100" hay "5 100" ⇒ {5, 100}; sai dạng ⇒ mảng rỗng (server vẫn kiểm biên). */
    static long[] parseQuantityAndPrice(String raw) {
        String[] parts = raw.strip().toLowerCase(Locale.ROOT).split("\\s*[x*\\s]\\s*", 2);
        if (parts.length != 2) {
            return new long[0];
        }
        long quantity = MarketCoordinator.parsePrice(parts[0]);
        long price = MarketCoordinator.parsePrice(parts[1]);
        if (quantity <= 0 || quantity > Integer.MAX_VALUE || price <= 0) {
            return new long[0];
        }
        return new long[] {quantity, price};
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
        byte[] payload = frame.payload();
        if (opcode == Opcode.OPCODE_LIST_BUY_ORDERS_RESPONSE_VALUE) {
            ListBuyOrdersResponse response = ListBuyOrdersResponse.parseFrom(payload);
            orders.clear();
            orders.addAll(response.getOrdersList());
            mine.clear();
            mine.addAll(response.getMineList());
            redraw();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            onBag(ListInventoryResponse.parseFrom(payload));
            return false; // KHÔNG nuốt — túi đồ, kho, phường thị cũng cần gói này
        }
        if (opcode == Opcode.OPCODE_CREATE_BUY_ORDER_RESPONSE_VALUE) {
            settle(CreateBuyOrderResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_SELL_TO_BUY_ORDER_RESPONSE_VALUE) {
            SellToBuyOrderResponse response = SellToBuyOrderResponse.parseFrom(payload);
            settle(response.getOutcome());
            if (response.getOutcome() == BuyOrderOutcome.BUY_ORDER_OUTCOME_OK) {
                chat.showSystemNotice(format("buyorder.notice.sold", response.getProceeds()));
            }
            return true;
        }
        if (opcode == Opcode.OPCODE_CANCEL_BUY_ORDER_RESPONSE_VALUE) {
            settle(CancelBuyOrderResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_ORDER_FILLED_EVENT_VALUE) {
            BuyOrderFilledEvent filled = BuyOrderFilledEvent.parseFrom(payload);
            chat.showSystemNotice(
                    format("buyorder.notice.filled", itemName(filled.getItemTemplateId()), filled.getQuantity()));
            return true;
        }
        return false;
    }

    private void onBag(ListInventoryResponse response) {
        List<Long> worn = response.getEquippedList().stream()
                .map(EquippedSlotView::getItemId)
                .toList();
        sellable.clear();
        for (InventoryItemView item : response.getItemsList()) {
            if (!worn.contains(item.getItemId()) && item.getEnhanceLevel() == 0) {
                sellable.add(item);
            }
        }
    }

    private void settle(BuyOrderOutcome outcome) {
        String suffix = outcome.name().substring("BUY_ORDER_OUTCOME_".length()).toLowerCase(Locale.ROOT);
        panel.setStatus(text("buyorder.outcome." + suffix));
        if (outcome == BuyOrderOutcome.BUY_ORDER_OUTCOME_OK) {
            request();
        }
    }

    private void redraw() {
        if (!panel.isOpen()) {
            return;
        }
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        if (tab == TAB_BROWSE) {
            for (BuyOrderView order : orders) {
                int remaining = order.getQuantityWanted() - order.getQuantityFilled();
                add(
                        rows,
                        orderText(order),
                        "buyorder.action.sell",
                        new Action(TAB_BROWSE, order.getOrderId(), remaining));
            }
        } else if (tab == TAB_MINE) {
            for (BuyOrderView order : mine) {
                add(rows, orderText(order), "buyorder.action.cancel", new Action(TAB_MINE, order.getOrderId(), 0));
            }
        } else {
            for (ItemCatalogEntry entry : itemCatalog.entries()) {
                add(rows, text(entry.getNameKey()), "buyorder.action.place", new Action(2, entry.getId(), 0));
            }
        }
        panel.setRows(rows);
    }

    private void add(List<ReportPanel.Row> rows, String label, String actionKey, Action action) {
        rows.add(new ReportPanel.Row(label, false, actionKey));
        actions.add(action);
    }

    private String orderText(BuyOrderView order) {
        return format(
                "buyorder.row",
                itemName(order.getItemTemplateId()),
                order.getQuantityFilled(),
                order.getQuantityWanted(),
                order.getUnitPrice());
    }

    private String itemName(long itemTemplateId) {
        return itemCatalog.nameKey(itemTemplateId).map(this::text).orElseGet(() -> "#" + itemTemplateId);
    }

    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object... args) {
        return KageI18n.formatOrKey(i18n, key, args);
    }

    @Override
    public void reset() {
        panel.close();
        orders.clear();
        mine.clear();
        sellable.clear();
        actions.clear();
        tab = TAB_BROWSE;
        panel.setRows(List.of());
    }
}
