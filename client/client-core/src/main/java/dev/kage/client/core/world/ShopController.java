package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.ShopListingView;

/**
 * Trạng thái cửa hàng (ADR-035 tiếp nối, shop.proto 1000-1005) — thuần Java, không libGDX/GL, test
 * được thẳng như {@link MailController}.
 *
 * <p>Giữ đúng bốn thứ: <b>danh mục</b> server vừa trả về, <b>món đang chọn để mua</b>, <b>số lượng
 * đang chọn</b>, và <b>khoá idempotency của lệnh Mua/Bán đang treo</b> (ADR-025) — tách riêng hai
 * khoá vì Mua và Bán là hai lệnh độc lập, có thể cùng treo một lúc về mặt giao thức (dù giao diện ở
 * lát cắt này chỉ bày lối Mua, xem javadoc {@link ShopCoordinator}).
 *
 * <p><b>{@code ShopListingView} không mang tên vật phẩm</b> (shop.proto: "client chưa có catalog để
 * tra tên/icon ở lát cắt này" — nay ĐÃ có, xem {@link ItemCatalogCoordinator}, ADR-035 opcode
 * 509/510). Lớp này vẫn chỉ giữ {@code item_template_id} trần; tra tên là việc của
 * {@link ShopCoordinator}, chỗ duy nhất biết cả catalog lẫn panel.
 *
 * <p><b>Giá hiển thị chỉ để NHÌN.</b> {@code buy_price}/{@code sell_price} ở đây là bản server vừa
 * gửi lúc hỏi danh mục — server tự tra lại giá THẬT lúc xử lý {@code BuyItemCommand}/
 * {@code SellItemCommand} và không tin số nào từ client (Điều 24, shop.proto). Giá có thể đã đổi
 * giữa lúc hỏi danh mục và lúc bấm Mua (người khác đổi giá, hoặc catalog cache cũ) — đó là lý do
 * outcome {@code BUY_OUTCOME_ITEM_NOT_IN_SHOP} tồn tại.
 */
final class ShopController {

    /** Số lượng tối thiểu một lần mua/bán — 0 món là vô nghĩa. */
    static final int MIN_QUANTITY = 1;

    /**
     * Trần số lượng NHẬP Ở CLIENT — chỉ để chặn một ô nhập gõ số khổng lồ, không phải luật kinh tế
     * thật (server tự kiểm túi đầy/đủ vàng theo Điều 24). {@code int32} của {@code quantity} trong
     * shop.proto cho phép lớn hơn nhiều; 999 là đủ cho MỌI stack thật của game này.
     */
    static final int MAX_QUANTITY = 999;

    /** Một dòng danh mục — xem javadoc lớp về vì sao KHÔNG có tên. */
    record Listing(long itemTemplateId, long buyPrice, long sellPrice) {

        /** {@code buyPrice == 0}: cửa hàng chỉ THU MUA món này (V55) — server cũng từ chối mua, đây chỉ để khỏi gửi. */
        boolean soldByShop() {
            return buyPrice > 0;
        }
    }

    /** Kết quả một lần bấm Mua, xem {@link #attemptBuy}. */
    sealed interface BuyAttempt {

        /** Hợp lệ ở mức client, gửi đi được. */
        record Send(long itemTemplateId, int quantity, String idempotencyKey) implements BuyAttempt {}

        /** Không gửi — món vừa chọn không còn trong danh mục (catalog vừa đổi dưới chân cú bấm). */
        record Reject(String messageKey) implements BuyAttempt {}
    }

    private final List<Listing> listings = new ArrayList<>();

    /** 0 = chưa chọn món nào để mua. */
    private long selectedItemTemplateId;

    /** Luôn nằm trong [{@link #MIN_QUANTITY}, {@link #MAX_QUANTITY}]. */
    private int selectedQuantity = MIN_QUANTITY;

    private long pendingBuyItemTemplateId;
    private int pendingBuyQuantity;
    private String pendingBuyKey = "";

    private long pendingSellItemId;
    private int pendingSellQuantity;
    private String pendingSellKey = "";

    /**
     * Danh mục mới nhất từ server — thay TOÀN BỘ, không trộn với bản cũ (shop.proto: luôn trả TOÀN
     * BỘ danh mục). Món đang chọn chỉ giữ lại nếu nó CÒN trong danh mục mới, cùng luật
     * {@link MailController#onMailList} dùng cho lá thư đang đọc.
     */
    void onListings(ListShopItemsResponse response) {
        listings.clear();
        for (ShopListingView view : response.getListingsList()) {
            listings.add(new Listing(view.getItemTemplateId(), view.getBuyPrice(), view.getSellPrice()));
        }
        if (findListing(selectedItemTemplateId).isEmpty()) {
            selectedItemTemplateId = 0L;
        }
    }

    /** Toàn bộ danh mục, đúng thứ tự server trả về. */
    List<Listing> listings() {
        return List.copyOf(listings);
    }

    Optional<Listing> selected() {
        return findListing(selectedItemTemplateId);
    }

    /** Chọn một món để mua. Id lạ thì KHÔNG đổi gì — danh mục có thể đã đổi dưới chân cú bấm. */
    void select(long itemTemplateId) {
        if (findListing(itemTemplateId).isPresent()) {
            selectedItemTemplateId = itemTemplateId;
        }
    }

    private Optional<Listing> findListing(long itemTemplateId) {
        if (itemTemplateId == 0L) {
            return Optional.empty();
        }
        for (Listing listing : listings) {
            if (listing.itemTemplateId() == itemTemplateId) {
                return Optional.of(listing);
            }
        }
        return Optional.empty();
    }

    /** Số lượng đang chọn để mua/bán. */
    int quantity() {
        return selectedQuantity;
    }

    /**
     * Đặt số lượng từ ô nhập chữ (giống {@code MailController} nhận vàng đính kèm dạng chuỗi). Chuỗi
     * không hợp lệ (rỗng/chữ/ngoài khoảng) thì GIỮ NGUYÊN giá trị cũ chứ không đặt về 0 — 0 món làm
     * nút Mua gửi một lệnh chắc chắn vô nghĩa.
     */
    void setQuantityText(String quantityText) {
        parseQuantity(quantityText).ifPresent(value -> selectedQuantity = value);
    }

    private static Optional<Integer> parseQuantity(String quantityText) {
        String trimmed = quantityText.strip();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        try {
            int value = Integer.parseInt(trimmed);
            return value < MIN_QUANTITY || value > MAX_QUANTITY ? Optional.empty() : Optional.of(value);
        } catch (NumberFormatException e) {
            // Người chơi gõ chữ vào ô số — nhánh bình thường của giao diện, không phải sự cố (Ngoại
            // lệ CÓ CHỦ ĐÍCH của /CLAUDE.md §11, cùng dạng MailController#parseGold).
            return Optional.empty();
        }
    }

    /**
     * Bấm Mua trên một dòng. Vừa CHỌN món đó (nếu còn hợp lệ) vừa sinh ra lệnh gửi đi được — hai việc
     * này chỉ tách rời trên lý thuyết trong giao diện ở lát cắt này (mỗi dòng có nút Mua riêng, không
     * qua bước "chọn rồi bấm Mua chung" của một nút khác).
     *
     * @return {@link BuyAttempt.Send} nếu món còn trong danh mục, {@link BuyAttempt.Reject} nếu
     *     không (danh mục vừa đổi dưới chân cú bấm — dùng LẠI khoá {@code shop.error.item_not_in_shop}
     *     server cũng dùng cho đúng tình huống này, cùng lý do {@code MailController} tái dùng khoá).
     */
    BuyAttempt attemptBuy(long itemTemplateId) {
        select(itemTemplateId);
        Optional<Listing> listing = findListing(itemTemplateId);
        if (listing.isEmpty()) {
            return new BuyAttempt.Reject("shop.error.item_not_in_shop");
        }
        if (!listing.get().soldByShop()) {
            return new BuyAttempt.Reject("shop.error.buy_back_only");
        }
        return new BuyAttempt.Send(itemTemplateId, selectedQuantity, beginBuy(itemTemplateId, selectedQuantity));
    }

    /**
     * Khoá idempotency cho một lệnh Mua (ADR-025).
     *
     * <p>Bấm lại Mua với ĐÚNG món và ĐÚNG số lượng đang treo trả LẠI đúng khoá cũ — đó là "tự thử lại
     * cùng một lần mua". Đổi món, đổi số lượng, hoặc bấm lại sau khi lệnh cũ đã có kết luận
     * ({@link #onBuySettled}) là một ý định MỚI và phải có khoá mới — cùng hình dạng
     * {@link MailController#beginClaim}.
     */
    private String beginBuy(long itemTemplateId, int quantity) {
        if (pendingBuyItemTemplateId != itemTemplateId || pendingBuyQuantity != quantity || pendingBuyKey.isEmpty()) {
            pendingBuyItemTemplateId = itemTemplateId;
            pendingBuyQuantity = quantity;
            pendingBuyKey = UUID.randomUUID().toString();
        }
        return pendingBuyKey;
    }

    /**
     * Lệnh Mua đang treo đã có kết luận — xoá khoá kể cả khi hỏng, cùng lý do
     * {@link MailController#onClaimSettled}.
     */
    void onBuySettled() {
        pendingBuyItemTemplateId = 0L;
        pendingBuyQuantity = 0;
        pendingBuyKey = "";
    }

    /** Món của lệnh Mua đang treo — 0 khi không có lệnh nào treo. Chỉ dùng để test. */
    long pendingBuyItemTemplateId() {
        return pendingBuyItemTemplateId;
    }

    /**
     * Khoá idempotency cho một lệnh Bán (ADR-025) — cùng luật {@link #beginBuy}, khoá theo cặp
     * {@code (itemId, quantity)} thay vì {@code itemTemplateId} vì {@code SellItemCommand} tham
     * chiếu {@code inventory_item.id} THẬT (shop.proto), không phải mã vật phẩm.
     *
     * <p>⚠️ Client-core CHƯA có giao diện chọn đồ để bán ở lát cắt này (giao diện túi đồ do một
     * luồng khác dựng song song) — hàm này tồn tại để {@link ShopCoordinator#sellItem} nối được vào
     * NGAY khi giao diện đó xong, không phải để gọi từ đâu trong {@link ShopPanel} bây giờ.
     */
    String beginSell(long itemId, int quantity) {
        if (pendingSellItemId != itemId || pendingSellQuantity != quantity || pendingSellKey.isEmpty()) {
            pendingSellItemId = itemId;
            pendingSellQuantity = quantity;
            pendingSellKey = UUID.randomUUID().toString();
        }
        return pendingSellKey;
    }

    /** Lệnh Bán đang treo đã có kết luận — xem {@link #onBuySettled} về lý do xoá cả khi hỏng. */
    void onSellSettled() {
        pendingSellItemId = 0L;
        pendingSellQuantity = 0;
        pendingSellKey = "";
    }

    /** Vật phẩm của lệnh Bán đang treo — 0 khi không có lệnh nào treo. Chỉ dùng để test. */
    long pendingSellItemId() {
        return pendingSellItemId;
    }

    /** Rời màn hình/mất kết nối — cửa hàng của một phiên chơi không được sống sót sang phiên sau. */
    void clear() {
        listings.clear();
        selectedItemTemplateId = 0L;
        selectedQuantity = MIN_QUANTITY;
        onBuySettled();
        onSellSettled();
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome — cùng cách MailController làm, và cùng lý do: một biểu
    // thức switch phủ hết một enum protobuf tự nó vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt ở cả hai bảng: cả hai rơi vào Optional.empty(), và
    // chỗ gọi dịch nó thành "shop.error.unknown" — kết quả của một server MỚI HƠN client, chỉ có thể
    // nói ra là không hiểu, không đoán bừa (Điều 24).
    // ---------------------------------------------------------------------------------------------

    private static final Map<BuyOutcome, String> BUY_MESSAGE_KEYS = Map.of(
            BuyOutcome.BUY_OUTCOME_OK, "shop.notice.bought",
            BuyOutcome.BUY_OUTCOME_ITEM_NOT_IN_SHOP, "shop.error.item_not_in_shop",
            BuyOutcome.BUY_OUTCOME_INSUFFICIENT_GOLD, "shop.error.insufficient_gold",
            BuyOutcome.BUY_OUTCOME_INVENTORY_FULL, "shop.error.inventory_full",
            BuyOutcome.BUY_OUTCOME_DUPLICATE_REQUEST, "shop.error.duplicate_request");

    // shop.error.duplicate_request DÙNG CHUNG với Mua — trùng khoá là ĐÚNG Ý (cùng lý do
    // MailController gộp khoá cho các outcome từ bản vá lỗ đúc vàng 2026-08-28): cùng một tình huống
    // (server thấy idempotency_key đã dùng) thì người chơi phải đọc cùng một câu, dù đang mua hay bán.
    private static final Map<SellOutcome, String> SELL_MESSAGE_KEYS = Map.of(
            SellOutcome.SELL_OUTCOME_OK, "shop.notice.sold",
            SellOutcome.SELL_OUTCOME_ITEM_NOT_FOUND, "shop.error.item_not_found",
            SellOutcome.SELL_OUTCOME_ITEM_NOT_SELLABLE, "shop.error.item_not_sellable",
            SellOutcome.SELL_OUTCOME_INSUFFICIENT_QUANTITY, "shop.error.insufficient_quantity",
            SellOutcome.SELL_OUTCOME_DUPLICATE_REQUEST, "shop.error.duplicate_request",
            SellOutcome.SELL_OUTCOME_ITEM_IN_TRADE, "shop.error.item_in_trade");

    private static <T> Optional<String> messageKey(Map<T, String> table, T outcome) {
        return Optional.ofNullable(table.get(outcome));
    }

    static Optional<String> buyMessageKey(BuyOutcome outcome) {
        return messageKey(BUY_MESSAGE_KEYS, outcome);
    }

    static Optional<String> sellMessageKey(SellOutcome outcome) {
        return messageKey(SELL_MESSAGE_KEYS, outcome);
    }
}
