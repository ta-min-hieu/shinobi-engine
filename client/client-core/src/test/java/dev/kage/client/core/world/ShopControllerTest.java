package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.BuyOutcome;
import dev.kage.protocol.ListShopItemsResponse;
import dev.kage.protocol.SellOutcome;
import dev.kage.protocol.ShopListingView;

/**
 * {@link ShopController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code MailControllerTest}.
 */
class ShopControllerTest {

    private final ShopController controller = new ShopController();

    @Test
    @DisplayName("Cửa hàng bắt đầu rỗng, chưa chọn món nào, số lượng mặc định là 1")
    void startsEmpty() {
        assertThat(controller.listings()).isEmpty();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.quantity()).isEqualTo(ShopController.MIN_QUANTITY);
        assertThat(controller.pendingBuyItemTemplateId()).isZero();
        assertThat(controller.pendingSellItemId()).isZero();
    }

    @Test
    @DisplayName("ListShopItemsResponse là nguồn DUY NHẤT của danh mục, giữ đủ 3 trường ShopListingView")
    void adoptsServerListings() {
        controller.onListings(listings(listing(7L, 100L, 40L)));

        assertThat(controller.listings()).containsExactly(new ShopController.Listing(7L, 100L, 40L));
    }

    @Test
    @DisplayName("Danh mục THAY hoàn toàn, không trộn với bản cũ")
    void replacesListingsInsteadOfMerging() {
        controller.onListings(listings(listing(1L, 10L, 5L), listing(2L, 20L, 10L)));
        controller.onListings(listings(listing(2L, 20L, 10L)));

        assertThat(controller.listings())
                .extracting(ShopController.Listing::itemTemplateId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("Chọn món: id có thật thì chọn được, id lạ thì KHÔNG đổi gì")
    void selectsOnlyExistingListing() {
        controller.onListings(listings(listing(5L, 10L, 4L)));

        controller.select(999L);
        assertThat(controller.selected()).isEmpty();

        controller.select(5L);
        assertThat(controller.selected())
                .map(ShopController.Listing::itemTemplateId)
                .contains(5L);
    }

    @Test
    @DisplayName("Món đang chọn biến mất khỏi danh mục mới thì BỎ chọn, không treo lựa chọn cũ")
    void dropsSelectionWhenListingDisappears() {
        controller.onListings(listings(listing(5L, 10L, 4L)));
        controller.select(5L);

        controller.onListings(ListShopItemsResponse.getDefaultInstance());

        assertThat(controller.selected()).isEmpty();
    }

    // ---------------------------------------------------------------------------------------------
    // Số lượng.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Số lượng: chuỗi hợp lệ đi qua, chữ/rỗng/ngoài khoảng GIỮ NGUYÊN giá trị cũ")
    void parsesQuantity() {
        controller.setQuantityText("5");
        assertThat(controller.quantity()).isEqualTo(5);

        controller.setQuantityText("nhiều");
        assertThat(controller.quantity()).isEqualTo(5);

        controller.setQuantityText("");
        assertThat(controller.quantity()).isEqualTo(5);

        controller.setQuantityText("0");
        assertThat(controller.quantity()).isEqualTo(5);

        controller.setQuantityText(Integer.toString(ShopController.MAX_QUANTITY + 1));
        assertThat(controller.quantity()).isEqualTo(5);

        controller.setQuantityText(Integer.toString(ShopController.MAX_QUANTITY));
        assertThat(controller.quantity()).isEqualTo(ShopController.MAX_QUANTITY);
    }

    // ---------------------------------------------------------------------------------------------
    // Bấm Mua.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Bấm Mua trên món có trong danh mục: chọn món đó và sinh khoá idempotency")
    void attemptBuySendsForKnownListing() {
        controller.onListings(listings(listing(7L, 100L, 40L)));
        controller.setQuantityText("3");

        ShopController.BuyAttempt attempt = controller.attemptBuy(7L);

        assertThat(attempt).isInstanceOf(ShopController.BuyAttempt.Send.class);
        ShopController.BuyAttempt.Send send = (ShopController.BuyAttempt.Send) attempt;
        assertThat(send.itemTemplateId()).isEqualTo(7L);
        assertThat(send.quantity()).isEqualTo(3);
        assertThat(java.util.UUID.fromString(send.idempotencyKey())).isNotNull();
        assertThat(controller.selected())
                .map(ShopController.Listing::itemTemplateId)
                .contains(7L);
        assertThat(controller.pendingBuyItemTemplateId()).isEqualTo(7L);
    }

    @Test
    @DisplayName("Bấm Mua trên món KHÔNG còn trong danh mục: từ chối, dùng lại khoá lỗi của server")
    void attemptBuyRejectsUnknownListing() {
        ShopController.BuyAttempt attempt = controller.attemptBuy(404L);

        assertThat(attempt).isEqualTo(new ShopController.BuyAttempt.Reject("shop.error.item_not_in_shop"));
        assertThat(controller.pendingBuyItemTemplateId()).isZero();
    }

    @Test
    @DisplayName("ADR-025: bấm Mua lại CÙNG món + CÙNG số lượng khi lệnh chưa xong giữ NGUYÊN khoá")
    void reusesBuyKeyForTheSameAttempt() {
        controller.onListings(listings(listing(7L, 100L, 40L)));
        controller.setQuantityText("2");

        ShopController.BuyAttempt.Send first = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);
        ShopController.BuyAttempt.Send second = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);

        assertThat(second.idempotencyKey()).isEqualTo(first.idempotencyKey());
    }

    @Test
    @DisplayName("ADR-025: đổi SỐ LƯỢNG trước khi bấm lại là một ý định khác — khoá phải khác")
    void newBuyKeyWhenQuantityChanges() {
        controller.onListings(listings(listing(7L, 100L, 40L)));
        controller.setQuantityText("2");
        ShopController.BuyAttempt.Send first = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);

        controller.setQuantityText("3");
        ShopController.BuyAttempt.Send second = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);

        assertThat(second.idempotencyKey()).isNotEqualTo(first.idempotencyKey());
    }

    @Test
    @DisplayName("ADR-025: mua MÓN KHÁC là một ý định khác — khoá phải khác")
    void newBuyKeyForADifferentItem() {
        controller.onListings(listings(listing(7L, 100L, 40L), listing(8L, 50L, 20L)));

        ShopController.BuyAttempt.Send first = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);
        ShopController.BuyAttempt.Send second = (ShopController.BuyAttempt.Send) controller.attemptBuy(8L);

        assertThat(second.idempotencyKey()).isNotEqualTo(first.idempotencyKey());
    }

    @Test
    @DisplayName("ADR-025: lệnh Mua đã có kết luận thì lần bấm sau (dù cùng món/số lượng) là ý định MỚI")
    void newBuyKeyAfterTheBuySettled() {
        controller.onListings(listings(listing(7L, 100L, 40L)));
        ShopController.BuyAttempt.Send first = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);

        controller.onBuySettled();
        assertThat(controller.pendingBuyItemTemplateId()).isZero();

        ShopController.BuyAttempt.Send second = (ShopController.BuyAttempt.Send) controller.attemptBuy(7L);
        assertThat(second.idempotencyKey()).isNotEqualTo(first.idempotencyKey());
    }

    // ---------------------------------------------------------------------------------------------
    // Bán — chưa có giao diện, nhưng khoá idempotency phải đúng luật y hệt Mua (ADR-025).
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("ADR-025: beginSell lặp lại CÙNG (itemId, số lượng) giữ NGUYÊN khoá")
    void reusesSellKeyForTheSameAttempt() {
        String first = controller.beginSell(42L, 2);
        String second = controller.beginSell(42L, 2);

        assertThat(second).isEqualTo(first);
        assertThat(controller.pendingSellItemId()).isEqualTo(42L);
    }

    @Test
    @DisplayName("ADR-025: đổi số lượng hoặc vật phẩm khi bán là một ý định khác — khoá phải khác")
    void newSellKeyForADifferentAttempt() {
        String first = controller.beginSell(42L, 2);

        assertThat(controller.beginSell(42L, 3)).isNotEqualTo(first);
        assertThat(controller.beginSell(43L, 2)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("ADR-025: lệnh Bán đã có kết luận thì lần sau là ý định MỚI, khoá mới")
    void newSellKeyAfterTheSellSettled() {
        String first = controller.beginSell(42L, 2);
        controller.onSellSettled();
        assertThat(controller.pendingSellItemId()).isZero();

        assertThat(controller.beginSell(42L, 2)).isNotEqualTo(first);
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n.
    // ---------------------------------------------------------------------------------------------

    /**
     * Quét HẾT hai enum thay vì liệt kê tay từng giá trị: thêm một outcome mới vào shop.proto mà quên
     * thêm khoá i18n sẽ làm test này đỏ ngay — bài học có thật của {@code MailController} (vòng gộp
     * trước server thêm outcome, client không có khoá, người chơi đọc "không rõ lỗi").
     */
    @Test
    @DisplayName("Mọi outcome THẬT của BuyOutcome/SellOutcome đều có khoá i18n riêng")
    void mapsEveryRealOutcome() {
        for (BuyOutcome outcome : BuyOutcome.values()) {
            if (isRealOutcome(outcome.name())) {
                assertThat(ShopController.buyMessageKey(outcome))
                        .as("BuyOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("shop."));
            }
        }
        for (SellOutcome outcome : SellOutcome.values()) {
            if (isRealOutcome(outcome.name())) {
                assertThat(ShopController.sellMessageKey(outcome))
                        .as("SellOutcome.%s chưa có khoá i18n", outcome)
                        .hasValueSatisfying(key -> assertThat(key).startsWith("shop."));
            }
        }
    }

    private static boolean isRealOutcome(String name) {
        return !name.endsWith("UNSPECIFIED") && !"UNRECOGNIZED".equals(name);
    }

    @Test
    @DisplayName("UNSPECIFIED/UNRECOGNIZED không có khoá — chỗ gọi dịch thành 'không hiểu' thay vì đoán")
    void leavesUnknownOutcomesUnmapped() {
        assertThat(ShopController.buyMessageKey(BuyOutcome.BUY_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(ShopController.buyMessageKey(BuyOutcome.UNRECOGNIZED)).isEmpty();
        assertThat(ShopController.sellMessageKey(SellOutcome.SELL_OUTCOME_UNSPECIFIED))
                .isEmpty();
        assertThat(ShopController.sellMessageKey(SellOutcome.UNRECOGNIZED)).isEmpty();
    }

    @Test
    @DisplayName("clear() dọn sạch: rời màn hình rồi vào lại không thấy cửa hàng của phiên trước")
    void clearResetsEverything() {
        controller.onListings(listings(listing(7L, 100L, 40L)));
        controller.select(7L);
        controller.setQuantityText("5");
        controller.attemptBuy(7L);
        controller.beginSell(42L, 2);

        controller.clear();

        assertThat(controller.listings()).isEmpty();
        assertThat(controller.selected()).isEmpty();
        assertThat(controller.quantity()).isEqualTo(ShopController.MIN_QUANTITY);
        assertThat(controller.pendingBuyItemTemplateId()).isZero();
        assertThat(controller.pendingSellItemId()).isZero();
    }

    // ---------------------------------------------------------------------------------------------
    // Bundle i18n — cùng bài kiểm với MailControllerTest, thu hẹp vào khoá shop.*.
    // ---------------------------------------------------------------------------------------------

    @Test
    @DisplayName("Mọi chuỗi tiếng Việt của cửa hàng nằm trong tập glyph font UI — không bị nuốt chữ")
    void everyVietnameseShopStringIsRenderable() {
        java.util.Properties vietnamese = bundle("strings_vi.properties");
        for (String key : shopKeys(vietnamese)) {
            String value = vietnamese.getProperty(key);
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                assertThat(dev.kage.client.core.render.FontFactory.canRender(character))
                        .as(
                                "%s chứa ký tự '%s' (U+%s) mà font UI KHÔNG vẽ được — chữ sẽ biến mất"
                                        + " lặng lẽ trên màn hình",
                                key, character, Integer.toHexString(character).toUpperCase(java.util.Locale.ROOT))
                        .isTrue();
            }
        }
    }

    @Test
    @DisplayName("Hai bundle có ĐÚNG cùng tập khoá shop.* — thiếu một bên là hiện thẳng khoá ra màn hình")
    void bothBundlesDeclareTheSameShopKeys() {
        assertThat(shopKeys(bundle("strings_vi.properties"))).isEqualTo(shopKeys(bundle("strings_en.properties")));
    }

    private static java.util.SortedSet<String> shopKeys(java.util.Properties properties) {
        java.util.SortedSet<String> keys = new java.util.TreeSet<>();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith("shop.")) {
                keys.add(key);
            }
        }
        assertThat(keys)
                .as("bundle không có khoá shop.* nào — đường dẫn resource sai?")
                .isNotEmpty();
        return keys;
    }

    /** Đọc bundle bằng UTF-8 tường minh: {@code Properties.load(InputStream)} mặc định ISO-8859-1. */
    private static java.util.Properties bundle(String fileName) {
        java.util.Properties properties = new java.util.Properties();
        java.io.InputStream stream = ShopControllerTest.class.getResourceAsStream("/i18n/" + fileName);
        assertThat(stream)
                .as("không tìm thấy /i18n/%s trên classpath", fileName)
                .isNotNull();
        // Reader trong try-with-resources chứ không phải InputStream: đóng Reader đóng luôn stream
        // bên dưới, còn đóng mỗi stream thì SpotBugs (OS_OPEN_STREAM) chỉ ra đúng rằng Reader bọc
        // ngoài không bao giờ được đóng.
        try (java.io.Reader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
        return properties;
    }

    // --- dựng dữ liệu --------------------------------------------------------------------------

    private static ListShopItemsResponse listings(ShopListingView... views) {
        ListShopItemsResponse.Builder builder = ListShopItemsResponse.newBuilder();
        for (ShopListingView view : views) {
            builder.addListings(view);
        }
        return builder.build();
    }

    private static ShopListingView listing(long itemTemplateId, long buyPrice, long sellPrice) {
        return ShopListingView.newBuilder()
                .setItemTemplateId(itemTemplateId)
                .setBuyPrice(buyPrice)
                .setSellPrice(sellPrice)
                .build();
    }
}
