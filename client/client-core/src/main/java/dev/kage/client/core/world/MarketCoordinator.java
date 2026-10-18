package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ReportPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyListingCommand;
import dev.kage.protocol.BuyListingResponse;
import dev.kage.protocol.CancelListingCommand;
import dev.kage.protocol.CancelListingResponse;
import dev.kage.protocol.CreateListingCommand;
import dev.kage.protocol.CreateListingResponse;
import dev.kage.protocol.EquippedSlotView;
import dev.kage.protocol.InventoryItemView;
import dev.kage.protocol.ListInventoryResponse;
import dev.kage.protocol.ListMarketRequest;
import dev.kage.protocol.ListMarketResponse;
import dev.kage.protocol.ListingSoldEvent;
import dev.kage.protocol.MarketListingView;
import dev.kage.protocol.MarketResult;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Phường thị (phím O, ADR-063): ba tab — Đang bán (nút "Mua"), Của tôi (nút "Huỷ"), Treo bán (túi đồ, nút
 * "Treo" dùng giá trong ô nhập). Treo/mua luôn CẢ chồng/cả dòng.
 *
 * <p>Client không tự quyết gì: giá, phí, đủ vàng, túi đầy, món đã cường hoá — server phán quyết và trả
 * {@code MarketResult}. Mỗi lần BẤM là một khoá chống trùng mới (ADR-025); server trả DUPLICATE thì coi
 * như đã xong.
 */
final class MarketCoordinator implements WindowFlow {

    static final List<String> TAB_KEYS = List.of("market.tab.browse", "market.tab.mine", "market.tab.sell");

    private static final int TAB_BROWSE = 0;
    private static final int TAB_MINE = 1;
    private static final int TAB_SELL = 2;

    private static final Map<MarketResult, String> RESULT_KEYS = new EnumMap<>(MarketResult.class);

    static {
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_OK, "market.status.ok");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_DUPLICATE_REQUEST, "market.status.ok");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_NOT_FOUND, "market.error.not_found");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_INVALID_QUANTITY, "market.error.not_found");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_INVALID_PRICE, "market.error.invalid_price");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_EQUIPPED, "market.error.equipped");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_ENHANCED_NOT_ALLOWED, "market.error.enhanced");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_ITEM_IN_TRADE, "market.error.in_trade");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_TOO_MANY_LISTINGS, "market.error.too_many");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_OWN_LISTING, "market.error.own_listing");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_INSUFFICIENT_GOLD, "market.error.insufficient_gold");
        RESULT_KEYS.put(MarketResult.MARKET_RESULT_BAG_FULL, "market.error.bag_full");
    }

    /** Hành động của một dòng có nút. */
    private record Action(int tab, long id, int quantity) {}

    private final ReportPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final ChatCoordinator notices;
    private final I18NBundle i18n = KageI18n.load();

    private final List<MarketListingView> listings = new ArrayList<>();
    private final List<MarketListingView> mine = new ArrayList<>();
    private final List<InventoryItemView> sellable = new ArrayList<>();

    /** Cùng chỉ số với {@link ReportPanel#setRows}; {@code null} = dòng không nút. */
    private final List<Action> actions = new ArrayList<>();

    private int tab = TAB_BROWSE;
    private int maxActive;
    private int feePercent;

    /**
     * @param feeds catalog vật phẩm (tên món) + nhật ký chat (báo "đã bán")
     */
    MarketCoordinator(ReportPanel panel, WorldCommandSender sender, ReportWindows.Feeds feeds) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.notices = feeds.chat();
        panel.setOnRefresh(this::request);
        panel.setOnTabSelected(this::selectTab);
        panel.setOnRowAction(this::onRowAction);
        panel.enableInput("market.input.price");
        panel.setStatus(text("market.hint.open"));
    }

    private void request() {
        sender.sendReadRequest(Opcode.OPCODE_LIST_MARKET_REQUEST_VALUE, ListMarketRequest.getDefaultInstance());
        sender.listInventory();
    }

    private void selectTab(int index) {
        tab = index;
        redraw();
    }

    private void onRowAction(int rowIndex) {
        if (rowIndex < 0 || rowIndex >= actions.size() || actions.get(rowIndex) == null) {
            return;
        }
        Action action = actions.get(rowIndex);
        switch (action.tab()) {
            case TAB_BROWSE ->
                sender.sendReadRequest(
                        Opcode.OPCODE_BUY_LISTING_COMMAND_VALUE,
                        BuyListingCommand.newBuilder()
                                .setListingId(action.id())
                                .setIdempotencyKey(UUID.randomUUID().toString())
                                .build());
            case TAB_MINE ->
                sender.sendReadRequest(
                        Opcode.OPCODE_CANCEL_LISTING_COMMAND_VALUE,
                        CancelListingCommand.newBuilder()
                                .setListingId(action.id())
                                .build());
            default -> createListing(action);
        }
    }

    /** Giá đọc từ ô nhập; chữ không phải số ⇒ báo ngay, không gửi (server vẫn kiểm lại biên). */
    private void createListing(Action action) {
        long price = parsePrice(panel.inputText());
        if (price <= 0) {
            panel.setStatus(text("market.error.invalid_price"));
            return;
        }
        sender.sendReadRequest(
                Opcode.OPCODE_CREATE_LISTING_COMMAND_VALUE,
                CreateListingCommand.newBuilder()
                        .setInventoryItemId(action.id())
                        .setQuantity(action.quantity())
                        .setPrice(price)
                        .setIdempotencyKey(UUID.randomUUID().toString())
                        .build());
    }

    static long parsePrice(String raw) {
        String digits = raw.strip().replace(".", "").replace(",", "");
        if (digits.isEmpty() || digits.length() > 12 || !digits.chars().allMatch(Character::isDigit)) {
            return -1L;
        }
        return Long.parseLong(digits);
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
        if (opcode == Opcode.OPCODE_LIST_MARKET_RESPONSE_VALUE) {
            onListings(ListMarketResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_INVENTORY_RESPONSE_VALUE) {
            onBag(ListInventoryResponse.parseFrom(payload));
            return false; // KHÔNG nuốt — túi đồ và kho cũng cần gói này
        }
        if (opcode == Opcode.OPCODE_CREATE_LISTING_RESPONSE_VALUE) {
            settle(CreateListingResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_LISTING_RESPONSE_VALUE) {
            settle(BuyListingResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_CANCEL_LISTING_RESPONSE_VALUE) {
            settle(CancelListingResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_LISTING_SOLD_EVENT_VALUE) {
            ListingSoldEvent sold = ListingSoldEvent.parseFrom(payload);
            notices.showSystemNotice(format(
                    "market.notice.sold", itemName(sold.getItemTemplateId()), sold.getQuantity(), sold.getProceeds()));
            if (panel.isOpen()) {
                request();
            }
            return true;
        }
        return false;
    }

    private void onListings(ListMarketResponse response) {
        listings.clear();
        listings.addAll(response.getListingsList());
        mine.clear();
        mine.addAll(response.getMineList());
        maxActive = response.getMaxActiveListings();
        feePercent = response.getFeePercent();
        if (panel.isOpen()) {
            redraw();
        }
    }

    /** Chỉ món có thể treo: không đang mặc, chưa cường hoá (D2) — server vẫn kiểm lại. */
    private void onBag(ListInventoryResponse response) {
        Set<Long> worn = new HashSet<>();
        for (EquippedSlotView slot : response.getEquippedList()) {
            worn.add(slot.getItemId());
        }
        sellable.clear();
        for (InventoryItemView item : response.getItemsList()) {
            if (!worn.contains(item.getItemId()) && item.getEnhanceLevel() == 0) {
                sellable.add(item);
            }
        }
        if (panel.isOpen() && tab == TAB_SELL) {
            redraw();
        }
    }

    private void settle(MarketResult result) {
        panel.setStatus(text(RESULT_KEYS.getOrDefault(result, "market.error.unknown")));
        if (result == MarketResult.MARKET_RESULT_OK) {
            request();
        }
    }

    /** Dựng dòng của tab đang chọn — chỉ khi server vừa trả lời hoặc đổi tab. */
    private void redraw() {
        List<ReportPanel.Row> rows = new ArrayList<>();
        actions.clear();
        switch (tab) {
            case TAB_BROWSE -> {
                header(rows, format("market.header.browse", feePercent));
                for (MarketListingView listing : listings) {
                    row(
                            rows,
                            listingText(listing, true),
                            "market.action.buy",
                            new Action(TAB_BROWSE, listing.getListingId(), 0));
                }
            }
            case TAB_MINE -> {
                header(rows, format("market.header.mine", mine.size(), maxActive));
                for (MarketListingView listing : mine) {
                    row(
                            rows,
                            listingText(listing, false),
                            "market.action.cancel",
                            new Action(TAB_MINE, listing.getListingId(), 0));
                }
            }
            default -> {
                header(rows, format("market.header.sell", feePercent));
                for (InventoryItemView item : sellable) {
                    row(
                            rows,
                            itemName(item.getItemTemplateId()) + quantityText(item.getQuantity()),
                            "market.action.list",
                            new Action(TAB_SELL, item.getItemId(), item.getQuantity()));
                }
            }
        }
        panel.setRows(rows);
    }

    private void header(List<ReportPanel.Row> rows, String text) {
        rows.add(new ReportPanel.Row(text, true));
        actions.add(null);
    }

    private void row(List<ReportPanel.Row> rows, String text, String actionKey, Action action) {
        rows.add(new ReportPanel.Row(text, false, actionKey));
        actions.add(action);
    }

    private String listingText(MarketListingView listing, boolean withSeller) {
        String base = format(
                "market.row.listing",
                itemName(listing.getItemTemplateId()) + quantityText(listing.getQuantity()),
                listing.getPrice());
        return withSeller ? base + " — " + listing.getSellerName() : base;
    }

    private static String quantityText(int quantity) {
        return quantity > 1 ? " x" + quantity : "";
    }

    /** Tên tra catalog; chưa có catalog thì hiện dạng số, không bịa (Điều 24). */
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
        listings.clear();
        mine.clear();
        sellable.clear();
        actions.clear();
        tab = TAB_BROWSE;
        panel.setRows(List.of());
        panel.setStatus(text("market.hint.open"));
    }
}
