package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptTradeRequestOutcome;
import dev.kage.protocol.CancelTradeOutcome;
import dev.kage.protocol.ConfirmTradeOutcome;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.RequestTradeOutcome;
import dev.kage.protocol.SetTradeOfferOutcome;
import dev.kage.protocol.TradeOfferItemView;
import dev.kage.protocol.TradePhase;
import dev.kage.protocol.TradeRequestedEvent;

/**
 * {@link TradeController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code ShopControllerTest}.
 */
class TradeControllerTest {

    private static final long SESSION_ID = 77L;

    /**
     * Nạp ở static initializer chứ không phải field của instance: {@link #bundle} có thể ném, và
     * ném trong constructor để lại một object dựng dở (SpotBugs {@code CT_CONSTRUCTOR_THROW}).
     */
    private static final Properties VIETNAMESE = bundle("strings_vi.properties");

    private static final Properties ENGLISH = bundle("strings_en.properties");

    private final TradeController controller = new TradeController();

    @Test
    @DisplayName("Chưa giao dịch gì thì không có phiên, không có lời mời")
    void startsEmpty() {
        assertThat(controller.inTrade()).isFalse();
        assertThat(controller.invite()).isEmpty();
        assertThat(controller.myItems()).isEmpty();
        assertThat(controller.theirItems()).isEmpty();
        assertThat(controller.pendingConfirmSessionId()).isZero();
    }

    @Test
    @DisplayName("GetTradeStatusResponse là nguồn DUY NHẤT của khung nhìn — giữ đủ cả hai lời chào")
    void adoptsServerStatus() {
        controller.onStatus(status(250L, 400L, true, false));

        assertThat(controller.inTrade()).isTrue();
        assertThat(controller.sessionId()).isEqualTo(SESSION_ID);
        assertThat(controller.counterpartName()).isEqualTo("Kagemaru");
        assertThat(controller.phase()).isEqualTo(TradePhase.TRADE_PHASE_OPEN);
        assertThat(controller.myGoldOffer()).isEqualTo(250L);
        assertThat(controller.theirGoldOffer()).isEqualTo(400L);
        assertThat(controller.myConfirmed()).isTrue();
        assertThat(controller.theirConfirmed()).isFalse();
        assertThat(controller.myItems()).containsExactly(new TradeController.OfferItem(11L, 5L, 2, 3));
    }

    @Test
    @DisplayName("in_trade=false xoá sạch phiên — không giữ lại mảnh nào của phiên cũ")
    void clearsWhenNotInTrade() {
        controller.onStatus(status(250L, 400L, true, true));

        controller.onStatus(
                GetTradeStatusResponse.newBuilder().setInTrade(false).build());

        assertThat(controller.inTrade()).isFalse();
        assertThat(controller.myGoldOffer()).isZero();
        assertThat(controller.myItems()).isEmpty();
        assertThat(controller.myConfirmed()).isFalse();
    }

    @Test
    @DisplayName("Cờ xác nhận đi theo ĐÚNG server — sửa lời chào làm server reset cả hai, client chỉ chép lại")
    void mirrorsConfirmFlagsFromServer() {
        controller.onStatus(status(250L, 400L, true, true));
        assertThat(controller.myConfirmed()).isTrue();
        assertThat(controller.theirConfirmed()).isTrue();

        // Đối phương vừa sửa lời chào ⇒ server reset cả hai cờ (GAMEPLAY.md §8) và gửi status mới.
        controller.onStatus(status(250L, 999L, false, false));

        assertThat(controller.myConfirmed()).isFalse();
        assertThat(controller.theirConfirmed()).isFalse();
    }

    @Test
    @DisplayName("offerWith dựng lời chào MỚI từ lời chào server đang giữ, không cộng dồn")
    void buildsNextOfferFromServerState() {
        controller.onStatus(status(0L, 0L, false, false));

        List<TradeController.OfferItem> added = controller.offerWith(42L, false);

        assertThat(added).extracting(TradeController.OfferItem::itemId).containsExactly(11L, 42L);
    }

    @Test
    @DisplayName("Đưa lại một món ĐÃ trong lời chào không nhân đôi nó")
    void doesNotDuplicateAnAlreadyOfferedItem() {
        controller.onStatus(status(0L, 0L, false, false));

        assertThat(controller.offerWith(11L, false))
                .extracting(TradeController.OfferItem::itemId)
                .containsExactly(11L);
    }

    @Test
    @DisplayName("offerWith(remove) bỏ đúng một món khỏi lời chào")
    void removesOneItemFromOffer() {
        controller.onStatus(status(0L, 0L, false, false));

        assertThat(controller.offerWith(11L, true)).isEmpty();
    }

    @Test
    @DisplayName("Bấm Xác nhận lại trên CÙNG phiên giữ nguyên khoá — đó là retry, không phải ý định mới")
    void keepsConfirmKeyOnRetry() {
        String first = controller.beginConfirm(SESSION_ID);

        assertThat(controller.beginConfirm(SESSION_ID)).isEqualTo(first);
    }

    @Test
    @DisplayName("Phiên khác thì khoá khác; xong một lệnh là quên khoá, kể cả khi lệnh hỏng")
    void issuesFreshConfirmKeyPerSession() {
        String first = controller.beginConfirm(SESSION_ID);

        assertThat(controller.beginConfirm(SESSION_ID + 1L)).isNotEqualTo(first);

        controller.onConfirmSettled();
        assertThat(controller.pendingConfirmSessionId()).isZero();
        assertThat(controller.beginConfirm(SESSION_ID + 1L)).isNotEqualTo(first);
    }

    @Test
    @DisplayName("Lời mời sau ĐÈ lời mời trước — chỉ trả lời được một cái")
    void latestInviteWins() {
        controller.onInvited(invite(1L, "Aoi"));
        controller.onInvited(invite(2L, "Benkei"));

        assertThat(controller.invite()).contains(new TradeController.Invite(2L, "Benkei"));
    }

    @Test
    @DisplayName("Ô vàng: rỗng = 0, số âm và chữ đều bị từ chối chứ không lặng lẽ thành 0")
    void parsesGold() {
        assertThat(TradeController.parseGold("")).contains(0L);
        assertThat(TradeController.parseGold("  250 ")).contains(250L);
        assertThat(TradeController.parseGold("-1")).isEmpty();
        assertThat(TradeController.parseGold("nhiều")).isEmpty();
        assertThat(TradeController.parseGold(null)).contains(0L);
    }

    @Test
    @DisplayName("MỌI outcome thật của cả năm enum đều có khoá i18n trong CẢ HAI bundle")
    void everyOutcomeHasAMessageKey() {
        for (RequestTradeOutcome outcome : RequestTradeOutcome.values()) {
            assertKeyed(outcome.name(), TradeController.requestMessageKey(outcome));
        }
        for (AcceptTradeRequestOutcome outcome : AcceptTradeRequestOutcome.values()) {
            assertKeyed(outcome.name(), TradeController.acceptMessageKey(outcome));
        }
        for (CancelTradeOutcome outcome : CancelTradeOutcome.values()) {
            assertKeyed(outcome.name(), TradeController.cancelMessageKey(outcome));
        }
        for (SetTradeOfferOutcome outcome : SetTradeOfferOutcome.values()) {
            assertKeyed(outcome.name(), TradeController.offerMessageKey(outcome));
        }
        for (ConfirmTradeOutcome outcome : ConfirmTradeOutcome.values()) {
            assertKeyed(outcome.name(), TradeController.confirmMessageKey(outcome));
        }
    }

    /** README dòng 25(i)(b): lỗi "đối phương vừa đổi lời chào" được nói nguyên văn; khoá lạ thì không. */
    @Test
    void shouldNameTheOfferJustChangedError_andNothingElse() {
        assertThat(TradeController.confirmErrorMessageKey("error.trade_offer_just_changed"))
                .contains("error.trade_offer_just_changed");
        assertThat(TradeController.confirmErrorMessageKey("error.internal")).isEmpty();
    }

    /**
     * {@code UNSPECIFIED} và {@code UNRECOGNIZED} KHÔNG phải kết quả thật (trade.proto) — chúng phải
     * rơi vào {@code Optional.empty()} để chỗ gọi dịch thành "không hiểu", chứ không được có khoá
     * riêng làm ra vẻ đã hiểu. Mọi outcome còn lại phải có khoá, và khoá đó phải nằm trong CẢ HAI
     * bundle — bài học thật: một vòng gộp trước server thêm outcome, client không có khoá, và người
     * chơi đọc "không rõ lỗi".
     */
    private static void assertKeyed(String outcomeName, Optional<String> key) {
        if (outcomeName.endsWith("_UNSPECIFIED") || "UNRECOGNIZED".equals(outcomeName)) {
            assertThat(key)
                    .as("%s KHÔNG được có khoá riêng — nó không phải kết quả thật", outcomeName)
                    .isEmpty();
            return;
        }
        assertThat(key)
                .as("%s thiếu khoá i18n — người chơi sẽ đọc 'không rõ lỗi'", outcomeName)
                .isPresent();
        assertThat(VIETNAMESE.getProperty(key.orElseThrow()))
                .as("strings_vi thiếu khoá %s (outcome %s)", key.orElseThrow(), outcomeName)
                .isNotNull();
        assertThat(ENGLISH.getProperty(key.orElseThrow()))
                .as("strings_en thiếu khoá %s (outcome %s)", key.orElseThrow(), outcomeName)
                .isNotNull();
    }

    // --- dựng dữ liệu --------------------------------------------------------------------------

    private static GetTradeStatusResponse status(
            long myGold, long theirGold, boolean myConfirmed, boolean theirConfirmed) {
        return GetTradeStatusResponse.newBuilder()
                .setInTrade(true)
                .setSessionId(SESSION_ID)
                .setPhase(TradePhase.TRADE_PHASE_OPEN)
                .setCounterpartName("Kagemaru")
                .setMyGoldOffer(myGold)
                .setTheirGoldOffer(theirGold)
                .setMyConfirmed(myConfirmed)
                .setTheirConfirmed(theirConfirmed)
                .addMyItems(item(11L, 5L, 2, 3))
                .addTheirItems(item(22L, 6L, 1, 0))
                .build();
    }

    private static TradeOfferItemView item(long itemId, long templateId, int quantity, int enhanceLevel) {
        return TradeOfferItemView.newBuilder()
                .setItemId(itemId)
                .setItemTemplateId(templateId)
                .setQuantity(quantity)
                .setEnhanceLevel(enhanceLevel)
                .build();
    }

    private static TradeRequestedEvent invite(long sessionId, String fromName) {
        return TradeRequestedEvent.newBuilder()
                .setSessionId(sessionId)
                .setFromName(fromName)
                .build();
    }

    /** Đọc bundle bằng UTF-8 tường minh: {@code Properties.load(InputStream)} mặc định ISO-8859-1. */
    private static Properties bundle(String fileName) {
        Properties properties = new Properties();
        InputStream stream = TradeControllerTest.class.getResourceAsStream("/i18n/" + fileName);
        assertThat(stream)
                .as("không tìm thấy /i18n/%s trên classpath", fileName)
                .isNotNull();
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            properties.load(reader);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return properties;
    }
}
