package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ShopPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.BuyItemResponse;
import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SellItemResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link ShopController} (trạng thái), {@link ShopPanel} (hiển thị), {@link WorldCommandSender}
 * (mạng), {@link ItemCatalogCoordinator} (tên vật phẩm) và {@link GoldCoordinator} (số dư) thành cửa
 * hàng chạy được (2026-08-29) — cùng hình dạng {@link MailCoordinator}.
 *
 * <p>Dải opcode 1000-1005 chưa từng được client dùng trước hôm nay, dù {@code feature-shop} phía
 * server đã xong từ Phase 5. Trước đó, bấm "Cho tôi xem hàng" trong hội thoại NPC chỉ đóng khung
 * thoại và hiện một câu "chưa mở được cửa hàng" — xem lịch sử ở {@link DialogueCoordinator}.
 *
 * <p><b>Hai lối vào, MỘT luồng xử lý.</b> Cửa hàng mở được từ phím B (gửi
 * {@code ListShopItemsRequest}, chờ {@code ListShopItemsResponse}) HOẶC từ hội thoại NPC (server tự
 * gửi {@code ListShopItemsResponse} khi lựa chọn hội thoại có {@code action="open_shop"}, xem
 * {@code GatewayChannelHandler.handleSelectDialogueOptionBlocking}). Cả hai chụm lại đúng một chỗ:
 * {@link #onListings}, chạy mỗi khi opcode 1001 tới bất kể vì sao nó tới. {@code PanelHub#dispatch}
 * hỏi MỌI luồng chứ không dừng ở luồng đầu tiên nhận, nên luồng này nhận được gói dù
 * {@link DialogueCoordinator} có đóng khung thoại của nó hay không — hai luồng không cần biết nhau.
 *
 * <p><b>⚠️ Bán vật phẩm — đường có sẵn, chưa có nút bấm.</b> {@link #sellItem} gửi lệnh + xử lý kết
 * quả ĐẦY ĐỦ (bao gồm khoá i18n cho mọi {@code SellOutcome}), nhưng {@link ShopPanel} KHÔNG có giao
 * diện chọn vật phẩm để bán — {@code SellItemCommand} tham chiếu {@code inventory_item.id} THẬT
 * (shop.proto), thứ chỉ có ý nghĩa cạnh một danh sách túi đồ, và giao diện túi đồ đang được một luồng
 * KHÁC dựng song song với lát cắt này (ADR-035). Nối nút Bán vào giao diện túi đồ là việc còn lại sau
 * khi hai luồng gộp.
 */
final class ShopCoordinator implements WindowFlow {

    private final ShopController controller = new ShopController();
    private final ShopPanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final GoldCoordinator gold;

    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link ShopPanel#setListings} — chỉ đổi khi server/catalog vừa cập nhật. */
    private final List<ShopPanel.ShopRow> rows = new ArrayList<>();

    /**
     * Hai luồng NỀN dùng chung (ADR-035: catalog vật phẩm + số vàng) — gộp lại để hạ tham số
     * constructor của {@link ShopCoordinator} xuống dưới ngưỡng {@code ParameterNumber} của
     * Checkstyle (max=4), cùng lý do {@link PlayContext} gộp {@code EcsWorld}+{@code InputMapper}
     * cho {@code WorldScreen}. Giữ tham chiếu SỐNG tới hai coordinator đã có ở {@code WorldScreen},
     * không phải value object — defensive-copy vô nghĩa (xem exclude SpotBugs
     * {@code EI_EXPOSE_REP}/{@code EI_EXPOSE_REP2} dành cho {@code PlayContext}, cùng lý do áp dụng
     * ở đây).
     */
    record SharedFeeds(ItemCatalogCoordinator itemCatalog, GoldCoordinator gold) {}

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím B — cổng phím CHUNG của mọi cửa sổ, xem
     *     {@link ShopPanel#setCanOpen}.
     */
    ShopCoordinator(ShopPanel panel, WorldCommandSender sender, SharedFeeds feeds, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = feeds.itemCatalog();
        this.gold = feeds.gold();
        panel.setCanOpen(canOpen);
        panel.setOnOpened(sender::listShopItems);
        panel.setOnQuantityChanged(controller::setQuantityText);
        panel.setOnBuy(this::onBuy);
        panel.setStatus(text("shop.hint.open"));
        itemCatalog.addCatalogListener(this::refreshListings);
        gold.addGoldListener(amount -> refreshGold());
        refreshListings();
        refreshGold();
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
        if (opcode == Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE) {
            onListings(ListShopItemsResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_BUY_ITEM_RESPONSE_VALUE) {
            onBuySettled(BuyItemResponse.parseFrom(payload).getOutcome());
            return true;
        }
        if (opcode == Opcode.OPCODE_SELL_ITEM_RESPONSE_VALUE) {
            onSellSettled(SellItemResponse.parseFrom(payload).getOutcome());
            return true;
        }
        return false;
    }

    /** Danh mục vừa về — xem javadoc lớp về hai lối vào chụm lại đây. */
    /**
     * Danh mục vừa về. Hiện cửa sổ bằng {@link ShopPanel#showListings()} chứ KHÔNG phải
     * {@link ShopPanel#open()}: {@code open()} hỏi lại danh mục, mà chính gói trả lời của lần hỏi đó
     * lại chạy vào đây — thành vòng lặp vô tận bắn gói liên tục lên server và khiến ESC không bao
     * giờ đóng được cửa sổ. Lỗi này đã lọt tới lúc chạy thật.
     *
     * <p>Mở ở đây là ĐÚNG chứ không thừa: server tự gửi gói này khi người chơi chọn "Cho tôi xem
     * hàng" trong hội thoại NPC, lúc client chưa hề mở cửa sổ nào.
     */
    private void onListings(ListShopItemsResponse response) {
        controller.onListings(response);
        refreshListings();
        panel.showListings();
    }

    private void onBuy(long itemTemplateId) {
        switch (controller.attemptBuy(itemTemplateId)) {
            case ShopController.BuyAttempt.Send send ->
                sender.buyItem(send.itemTemplateId(), send.quantity(), send.idempotencyKey());
            case ShopController.BuyAttempt.Reject reject -> panel.setStatus(text(reject.messageKey()));
        }
    }

    /**
     * Kết quả lệnh Mua. KHÔNG hỏi lại danh mục (khác {@code MailCoordinator#settle}): shop.proto
     * không có khái niệm "tồn kho" trên {@code ShopListingView} (chỉ giá mua/bán), nên một lần mua
     * thành công không làm danh mục đổi. Số vàng tự cập nhật qua {@code GoldChangedEvent} riêng
     * ({@link GoldCoordinator}), không qua đường này.
     */
    private void onBuySettled(BuyOutcome outcome) {
        controller.onBuySettled();
        panel.setStatus(text(ShopController.buyMessageKey(outcome).orElse("shop.error.unknown")));
    }

    private void onSellSettled(SellOutcome outcome) {
        controller.onSellSettled();
        panel.setStatus(text(ShopController.sellMessageKey(outcome).orElse("shop.error.unknown")));
    }

    /**
     * Bán một vật phẩm THẬT — xem cảnh báo "chưa có nút bấm" ở javadoc lớp. Giao diện túi đồ gọi hàm
     * này khi người chơi bấm Bán trên một món trong túi.
     *
     * @param itemId {@code inventory_item.id} THẬT của món trong túi (server tự tra lại giá, KHÔNG
     *     tin số nào từ client — Điều 24).
     * @param quantity số lượng muốn bán.
     */
    void sellItem(long itemId, int quantity) {
        sender.sellItem(itemId, quantity, controller.beginSell(itemId, quantity));
    }

    /** Đẩy toàn bộ danh mục hiện có xuống panel. Gọi sau MỖI lần server trả lời hoặc catalog đổi tên. */
    private void refreshListings() {
        List<ShopController.Listing> all = controller.listings();
        int shown = Math.min(all.size(), ShopPanel.MAX_VISIBLE_ROWS);
        rows.clear();
        for (int index = 0; index < shown; index++) {
            ShopController.Listing listing = all.get(index);
            rows.add(new ShopPanel.ShopRow(listing.itemTemplateId(), rowText(listing)));
        }
        panel.setListings(rows, listNotice(all.size(), all.size() - shown));
    }

    /** Một dòng danh mục: {@code Tên — Mua: X · Bán: Y}. */
    private String rowText(ShopController.Listing listing) {
        return itemName(listing.itemTemplateId()) + " — " + format("shop.label.buy_price", listing.buyPrice()) + " · "
                + format("shop.label.sell_price", listing.sellPrice());
    }

    /**
     * Tên vật phẩm, tra qua {@link ItemCatalogCoordinator}. RỖNG (chưa có catalog, hoặc id lạ) hiện
     * dạng số — TUYỆT ĐỐI không bịa tên (Điều 24), cùng lý do {@code MailCoordinator#rewardText} phải
     * làm vậy trước khi có catalog.
     */
    private String itemName(long itemTemplateId) {
        return itemCatalog
                .nameKey(itemTemplateId)
                .map(this::text)
                .orElseGet(() -> format("shop.item.raw_id", itemTemplateId));
    }

    private void refreshGold() {
        panel.setGoldText(
                gold.hasAmount() ? format("shop.label.gold", gold.amount()) : text("shop.label.gold_unknown"));
    }

    /** Câu nói thêm dưới danh mục — cùng lý do {@code MailCoordinator#listNotice}. */
    private String listNotice(int total, int hidden) {
        if (total == 0) {
            return text("shop.empty");
        }
        return hidden <= 0 ? "" : format("shop.list.truncated", hidden);
    }

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

    /** Rời màn hình/mất kết nối — cửa hàng của một phiên chơi không được sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("shop.hint.open"));
        refreshListings();
        refreshGold();
    }
}
