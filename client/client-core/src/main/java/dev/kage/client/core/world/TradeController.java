package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

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
 * Trạng thái giao dịch phía client (trade.proto, dải 1400-1414) — thuần Java, không libGDX/GL, test
 * được thẳng như {@link ShopController}.
 *
 * <p><b>Server là nguồn chân lý DUY NHẤT của toàn bộ khung nhìn.</b> Lớp này không tự suy ra gì:
 * mọi thứ hiển thị đến từ {@link GetTradeStatusResponse} gần nhất. {@code TradeUpdatedEvent} cố ý
 * KHÔNG mang trạng thái (trade.proto) — nó chỉ nói "có gì đó đổi rồi, hỏi lại đi", nên
 * {@link TradeCoordinator} hỏi lại và ném kết quả vào đây.
 *
 * <p><b>Vì sao KHÔNG có "lời chào nháp" riêng.</b> {@code SetTradeOfferCommand} đặt lại TOÀN BỘ lời
 * chào chứ không cộng dồn, nên thêm một món = gửi lại cả danh sách. Giữ một bản nháp cục bộ song
 * song với {@code my_items} của server là tạo ra hai sự thật cho cùng một thứ, và chúng sẽ lệch nhau
 * đúng vào lúc quan trọng nhất. Thay vào đó {@link #offerWith} dựng danh sách MỚI từ chính
 * {@code my_items} server vừa trả về — đọc xong là quên.
 *
 * <p><b>Sửa lời chào làm reset CẢ HAI cờ confirmed</b> (trade.proto, GAMEPLAY.md §8 chống lừa đảo).
 * Client không cần làm gì để đảm bảo điều đó — server tự lo — nhưng giao diện phải hiện cờ confirmed
 * theo đúng những gì server nói, đừng nhớ cờ cũ.
 */
final class TradeController {

    /** Số vàng tối thiểu chào được. Chào 0 vàng là hợp lệ (giao dịch chỉ có vật phẩm). */
    static final long MIN_GOLD = 0L;

    /**
     * Số lượng của một lần đưa món vào lời chào. Cùng lý do {@code ShopCoordinator#sellOne}: túi đồ
     * không có ô nhập số lượng, và đưa nhầm cả chồng đồ vào một giao dịch là thứ rất khó nhận ra.
     */
    static final int SINGLE_ITEM = 1;

    /** Khoá lỗi server trả khi đối phương vừa đổi lời chào (README dòng 25(i)(b)). */
    private static final String TRADE_OFFER_JUST_CHANGED = "error.trade_offer_just_changed";

    /** Một món trong lời chào của một bên. */
    record OfferItem(long itemId, long itemTemplateId, int quantity, int enhanceLevel) {}

    /**
     * Lời mời giao dịch vừa nhận, chưa trả lời. Tách khỏi phiên đang mở: người chơi có thể nhận lời
     * mời trong lúc chưa ở giao dịch nào, và {@code session_id} của lời mời chính là thứ
     * {@code AcceptTradeRequestCommand} cần.
     */
    record Invite(long sessionId, String fromName) {}

    private boolean inTrade;
    private long sessionId;
    private TradePhase phase = TradePhase.TRADE_PHASE_UNSPECIFIED;
    private String counterpartName = "";
    private long myGoldOffer;
    private long theirGoldOffer;
    private boolean myConfirmed;
    private boolean theirConfirmed;

    private final List<OfferItem> myItems = new ArrayList<>();
    private final List<OfferItem> theirItems = new ArrayList<>();

    private Invite invite;

    /**
     * Khoá idempotency của lệnh Xác nhận đang treo (ADR-025) — {@code ConfirmTradeCommand} là lệnh
     * DUY NHẤT ở feature-trade có thể chuyển vàng/vật phẩm thật (trade.proto).
     */
    private PendingConfirm pendingConfirm = PendingConfirm.NONE;

    /**
     * Lệnh Xác nhận đang treo: phiên của nó + khoá idempotency. Một giá trị chứ không phải hai field rời —
     * hai số này luôn đổi cùng nhau (và giữ lớp trong trần ≤10 field, dòng 58(d), 2026-10-17).
     */
    private record PendingConfirm(long sessionId, String idempotencyKey) {
        static final PendingConfirm NONE = new PendingConfirm(0L, "");
    }

    /** Nhận ảnh chụp trạng thái mới nhất — lối vào DUY NHẤT của mọi thứ hiển thị. */
    void onStatus(GetTradeStatusResponse response) {
        inTrade = response.getInTrade();
        if (!inTrade) {
            clearSession();
            return;
        }
        sessionId = response.getSessionId();
        phase = response.getPhase();
        counterpartName = response.getCounterpartName();
        myGoldOffer = response.getMyGoldOffer();
        theirGoldOffer = response.getTheirGoldOffer();
        myConfirmed = response.getMyConfirmed();
        theirConfirmed = response.getTheirConfirmed();
        copyItems(response.getMyItemsList(), myItems);
        copyItems(response.getTheirItemsList(), theirItems);
    }

    private static void copyItems(List<TradeOfferItemView> from, List<OfferItem> into) {
        into.clear();
        for (TradeOfferItemView view : from) {
            into.add(new OfferItem(
                    view.getItemId(), view.getItemTemplateId(), view.getQuantity(), view.getEnhanceLevel()));
        }
    }

    /** Có người vừa mời giao dịch. Lời mời sau ĐÈ lời mời trước — chỉ trả lời được một cái. */
    void onInvited(TradeRequestedEvent event) {
        invite = new Invite(event.getSessionId(), event.getFromName());
    }

    /** Phiên đã đóng (huỷ/từ chối/thi hành xong) hoặc rời màn hình. */
    void clearSession() {
        inTrade = false;
        sessionId = 0L;
        phase = TradePhase.TRADE_PHASE_UNSPECIFIED;
        counterpartName = "";
        myGoldOffer = 0L;
        theirGoldOffer = 0L;
        myConfirmed = false;
        theirConfirmed = false;
        myItems.clear();
        theirItems.clear();
        onConfirmSettled();
    }

    /** Quên lời mời chưa trả lời — sau khi đã Chấp nhận hoặc Từ chối nó. */
    void clearInvite() {
        invite = null;
    }

    void clear() {
        clearSession();
        clearInvite();
    }

    boolean inTrade() {
        return inTrade;
    }

    long sessionId() {
        return sessionId;
    }

    TradePhase phase() {
        return phase;
    }

    String counterpartName() {
        return counterpartName;
    }

    long myGoldOffer() {
        return myGoldOffer;
    }

    long theirGoldOffer() {
        return theirGoldOffer;
    }

    boolean myConfirmed() {
        return myConfirmed;
    }

    boolean theirConfirmed() {
        return theirConfirmed;
    }

    List<OfferItem> myItems() {
        return List.copyOf(myItems);
    }

    List<OfferItem> theirItems() {
        return List.copyOf(theirItems);
    }

    Optional<Invite> invite() {
        return Optional.ofNullable(invite);
    }

    /**
     * Danh sách vật phẩm cho một {@code SetTradeOfferCommand} MỚI = lời chào server đang giữ, cộng
     * hoặc trừ đúng một món.
     *
     * <p>Đưa một món ĐÃ có trong lời chào vào lần nữa sẽ không nhân đôi nó: giao thức tham chiếu
     * {@code inventory_item.id} THẬT, mà một id chỉ trỏ tới đúng một dòng túi đồ.
     *
     * @param itemId món cần thêm, hoặc {@code 0} để chỉ dựng lại danh sách hiện có
     * @param remove {@code true} thì bỏ {@code itemId} ra khỏi lời chào thay vì thêm vào
     */
    List<OfferItem> offerWith(long itemId, boolean remove) {
        List<OfferItem> next = new ArrayList<>();
        for (OfferItem item : myItems) {
            if (!remove || item.itemId() != itemId) {
                next.add(item);
            }
        }
        if (!remove && itemId != 0L && next.stream().noneMatch(item -> item.itemId() == itemId)) {
            // itemTemplateId/enhanceLevel để 0: chỉ itemId đi lên dây (TradeOfferItemInput), phần
            // còn lại là thứ server trả về ở lần status kế tiếp.
            next.add(new OfferItem(itemId, 0L, SINGLE_ITEM, 0));
        }
        return next;
    }

    /**
     * Khoá idempotency cho một lần Xác nhận (ADR-025) — cùng luật {@code ShopController#beginBuy}:
     * GIỮ NGUYÊN khoá khi bấm lại trên CÙNG một phiên (đó là retry của cùng một ý định), sinh khoá
     * mới khi sang phiên khác.
     */
    String beginConfirm(long forSessionId) {
        if (pendingConfirm.sessionId() != forSessionId
                || pendingConfirm.idempotencyKey().isEmpty()) {
            pendingConfirm = new PendingConfirm(forSessionId, UUID.randomUUID().toString());
        }
        return pendingConfirm.idempotencyKey();
    }

    /**
     * Lệnh Xác nhận đang treo đã có kết luận — xoá khoá KỂ CẢ khi hỏng, cùng lý do
     * {@code ShopController#onBuySettled}: giữ lại chỉ tạo khả năng gán nhầm khoá cũ cho một ý định
     * mới.
     */
    void onConfirmSettled() {
        pendingConfirm = PendingConfirm.NONE;
    }

    /** Phiên của lệnh Xác nhận đang treo — 0 khi không có lệnh nào treo. Chỉ dùng để test. */
    long pendingConfirmSessionId() {
        return pendingConfirm.sessionId();
    }

    /**
     * Số vàng người chơi gõ vào ô chào vàng.
     *
     * @return {@link Optional#empty()} nếu không phải số nguyên không âm — chỗ gọi phải từ chối gửi
     *     và nói ra, đừng lặng lẽ gửi 0
     */
    static Optional<Long> parseGold(String goldText) {
        String trimmed = goldText == null ? "" : goldText.trim();
        if (trimmed.isEmpty()) {
            return Optional.of(MIN_GOLD);
        }
        try {
            long parsed = Long.parseLong(trimmed);
            return parsed < MIN_GOLD ? Optional.empty() : Optional.of(parsed);
        } catch (NumberFormatException e) {
            // Không log: người chơi gõ sai một ô số là chuyện thường ngày, không phải sự kiện hệ thống.
            return Optional.empty();
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n cho từng outcome — cùng cách ShopController/MailController làm, và cùng lý
    // do: một biểu thức switch phủ hết một enum protobuf tự nó vượt trần CyclomaticComplexity của PMD.
    //
    // UNSPECIFIED và UNRECOGNIZED CỐ TÌNH vắng mặt ở mọi bảng: cả hai rơi vào Optional.empty(), và
    // chỗ gọi dịch nó thành "trade.error.unknown" — kết quả của một server MỚI HƠN client, chỉ có
    // thể nói ra là không hiểu, không đoán bừa (Điều 24).
    // ---------------------------------------------------------------------------------------------

    private static final Map<RequestTradeOutcome, String> REQUEST_MESSAGE_KEYS = Map.of(
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_OK, "trade.notice.requested",
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_CANNOT_TRADE_SELF, "trade.error.cannot_trade_self",
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_ALREADY_IN_TRADE, "trade.error.already_in_trade",
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_TARGET_ALREADY_IN_TRADE, "trade.error.target_already_in_trade",
            RequestTradeOutcome.REQUEST_TRADE_OUTCOME_TARGET_NOT_FOUND, "trade.error.target_not_found");

    private static final Map<AcceptTradeRequestOutcome, String> ACCEPT_MESSAGE_KEYS = Map.of(
            AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_OK, "trade.notice.accepted",
            AcceptTradeRequestOutcome.ACCEPT_TRADE_REQUEST_OUTCOME_NOT_FOUND, "trade.error.not_found");

    private static final Map<CancelTradeOutcome, String> CANCEL_MESSAGE_KEYS = Map.of(
            CancelTradeOutcome.CANCEL_TRADE_OUTCOME_OK, "trade.notice.cancelled",
            CancelTradeOutcome.CANCEL_TRADE_OUTCOME_NOT_FOUND, "trade.error.not_found");

    private static final Map<SetTradeOfferOutcome, String> OFFER_MESSAGE_KEYS = Map.of(
            SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_OK, "trade.notice.offer_set",
            SetTradeOfferOutcome.SET_TRADE_OFFER_OUTCOME_NOT_FOUND, "trade.error.not_found");

    // OK và EXECUTED là HAI câu khác nhau có chủ đích: "đã xác nhận, đang chờ đối phương" với "giao
    // dịch xong rồi" là hai tình huống người chơi phải phân biệt được ngay.
    private static final Map<ConfirmTradeOutcome, String> CONFIRM_MESSAGE_KEYS = Map.of(
            ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OK, "trade.notice.confirmed_waiting",
            ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_EXECUTED, "trade.notice.executed",
            ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_NOT_FOUND, "trade.error.not_found",
            ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_OFFER_INVALID, "trade.error.offer_invalid",
            ConfirmTradeOutcome.CONFIRM_TRADE_OUTCOME_DUPLICATE_REQUEST, "trade.error.duplicate_request");

    private static <T> Optional<String> messageKey(Map<T, String> table, T outcome) {
        return Optional.ofNullable(table.get(outcome));
    }

    static Optional<String> requestMessageKey(RequestTradeOutcome outcome) {
        return messageKey(REQUEST_MESSAGE_KEYS, outcome);
    }

    static Optional<String> acceptMessageKey(AcceptTradeRequestOutcome outcome) {
        return messageKey(ACCEPT_MESSAGE_KEYS, outcome);
    }

    static Optional<String> cancelMessageKey(CancelTradeOutcome outcome) {
        return messageKey(CANCEL_MESSAGE_KEYS, outcome);
    }

    static Optional<String> offerMessageKey(SetTradeOfferOutcome outcome) {
        return messageKey(OFFER_MESSAGE_KEYS, outcome);
    }

    static Optional<String> confirmMessageKey(ConfirmTradeOutcome outcome) {
        return messageKey(CONFIRM_MESSAGE_KEYS, outcome);
    }

    /**
     * Khoá i18n cho một {@code ErrorResponse} trả về lệnh Xác nhận — chỉ khoá đã biết mới được nói
     * nguyên văn, khoá lạ rơi về {@code trade.error.unknown} ở {@code TradeCoordinator#settle}.
     */
    static Optional<String> confirmErrorMessageKey(String serverErrorKey) {
        return TRADE_OFFER_JUST_CHANGED.equals(serverErrorKey) ? Optional.of(serverErrorKey) : Optional.empty();
    }
}
