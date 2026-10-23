package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.ScrollingList;
import dev.kage.client.core.ui.hud.TradePanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptTradeRequestResponse;
import dev.kage.protocol.CancelTradeResponse;
import dev.kage.protocol.ConfirmTradeResponse;
import dev.kage.protocol.ErrorResponse;
import dev.kage.protocol.GetTradeStatusResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RequestTradeResponse;
import dev.kage.protocol.SetTradeOfferResponse;
import dev.kage.protocol.TradeCloseReason;
import dev.kage.protocol.TradeClosedEvent;
import dev.kage.protocol.TradePhase;
import dev.kage.protocol.TradeRequestedEvent;
import dev.kage.protocol.TradeUpdatedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link TradeController} (trạng thái), {@link TradePanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành giao dịch chạy được (trade.proto 1400-1414) — cùng hình
 * dạng {@link MailCoordinator}.
 *
 * <p><b>Luật một dòng: sau MỌI thứ có thể đổi phiên, hỏi lại server</b> ({@link #settle}). Giao
 * dịch là "hệ thống nguy hiểm nhất" (GAMEPLAY.md §8) nên client tuyệt đối không suy diễn trạng thái
 * mới từ một câu trả lời OK: nó hỏi lại {@code GetTradeStatusRequest} và vẽ đúng những gì server
 * nói. {@code TradeUpdatedEvent} cố ý không mang trạng thái, chính là để ép nếp đó.
 *
 * <p>Tra tên vật phẩm qua {@link ItemCatalogCoordinator} (ADR-035) — chưa có catalog thì hiện dạng
 * số, KHÔNG bịa (Điều 24). {@code TradeOfferItemView} còn có một trạng thái riêng cần nói thật:
 * {@code item_template_id = 0} nghĩa là món đó đã bị bán/gửi/xoá giữa chừng (tham chiếu mềm,
 * trade.proto) — hiện ra là "vật phẩm không còn nữa" chứ không im lặng bỏ dòng đó đi, vì nó là lý do
 * lần Xác nhận sắp tới sẽ trả {@code OFFER_INVALID}.
 */
final class TradeCoordinator implements WindowFlow {

    private final TradeController controller = new TradeController();
    private final TradePanel panel;
    private final WorldCommandSender sender;
    private final ItemCatalogCoordinator itemCatalog;
    private final I18NBundle i18n = KageI18n.load();

    /** Không có lệnh Xác nhận nào đang chờ trả lời — sequenceId thật luôn dương. */
    private static final int NO_PENDING_CONFIRM = 0;

    /** sequenceId của lệnh Xác nhận đang chờ — để nhận đúng {@code ErrorResponse} của nó. */
    private int pendingConfirmSequenceId = NO_PENDING_CONFIRM;

    /** Đệm dựng sẵn cho {@link TradePanel#setItems} — chỉ đổi khi server vừa trả lời. */
    private final List<String> itemRows = new ArrayList<>();

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím N — cổng phím CHUNG của mọi cửa sổ, xem
     *     {@link PanelHub#noneOpen()}.
     */
    TradeCoordinator(
            TradePanel panel, WorldCommandSender sender, ItemCatalogCoordinator itemCatalog, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.itemCatalog = itemCatalog;
        panel.setCanOpen(canOpen);
        panel.setOnRefresh(sender::getTradeStatus);
        panel.setOnRequest(sender::requestTrade);
        panel.setOnAcceptInvite(this::acceptInvite);
        panel.setOnDeclineInvite(this::declineInvite);
        panel.setOnSetGold(this::setGold);
        panel.setOnConfirm(this::confirm);
        panel.setOnCancel(this::cancel);
        panel.setStatus(text("trade.hint.open"));
        itemCatalog.addCatalogListener(this::refreshView);
        refreshView();
    }

    /** Có đang ở trong một phiên giao dịch không — giao diện túi đồ hỏi câu này để bày nút Giao dịch. */
    boolean inTrade() {
        return controller.inTrade();
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
        if (opcode == Opcode.OPCODE_GET_TRADE_STATUS_RESPONSE_VALUE) {
            controller.onStatus(GetTradeStatusResponse.parseFrom(payload));
            refreshView();
            return true;
        }
        if (opcode == Opcode.OPCODE_TRADE_REQUESTED_EVENT_VALUE) {
            onInvited(TradeRequestedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_TRADE_UPDATED_EVENT_VALUE) {
            // Event KHÔNG mang trạng thái (trade.proto) — nó chỉ nói "có gì đó đổi rồi". Vẫn ĐỌC nó
            // trước khi hỏi lại: payload rác phải dừng ở parse như mọi gói khác, không được kéo theo
            // một lệnh gửi lên (InboundPacketFuzzTest).
            TradeUpdatedEvent.parser().parseFrom(payload);
            sender.getTradeStatus();
            return true;
        }
        if (opcode == Opcode.OPCODE_TRADE_CLOSED_EVENT_VALUE) {
            onClosed(TradeClosedEvent.parseFrom(payload));
            return true;
        }
        return dispatchCommandResponse(opcode, payload);
    }

    /** Tách nhóm gói trả lời lệnh THUẦN để hạ cyclomatic complexity dưới ngưỡng PMD (max 10). */
    private boolean dispatchCommandResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_ERROR_VALUE) {
            return onConfirmRejected(ErrorResponse.parseFrom(payload));
        }
        if (opcode == Opcode.OPCODE_REQUEST_TRADE_RESPONSE_VALUE) {
            RequestTradeResponse response = RequestTradeResponse.parseFrom(payload);
            settle(TradeController.requestMessageKey(response.getOutcome()));
            return true;
        }
        if (opcode == Opcode.OPCODE_ACCEPT_TRADE_REQUEST_RESPONSE_VALUE) {
            AcceptTradeRequestResponse response = AcceptTradeRequestResponse.parseFrom(payload);
            controller.clearInvite();
            settle(TradeController.acceptMessageKey(response.getOutcome()));
            return true;
        }
        if (opcode == Opcode.OPCODE_CANCEL_TRADE_RESPONSE_VALUE) {
            CancelTradeResponse response = CancelTradeResponse.parseFrom(payload);
            settle(TradeController.cancelMessageKey(response.getOutcome()));
            return true;
        }
        if (opcode == Opcode.OPCODE_SET_TRADE_OFFER_RESPONSE_VALUE) {
            SetTradeOfferResponse response = SetTradeOfferResponse.parseFrom(payload);
            settle(TradeController.offerMessageKey(response.getOutcome()));
            return true;
        }
        if (opcode == Opcode.OPCODE_CONFIRM_TRADE_RESPONSE_VALUE) {
            onConfirmSettled(ConfirmTradeResponse.parseFrom(payload));
            return true;
        }
        return false;
    }

    /**
     * Nói ra kết quả một lệnh, rồi HỎI LẠI server — không suy diễn trạng thái mới từ một chữ OK.
     * Xem javadoc lớp về lý do nếp này chặt hơn ở giao dịch so với các luồng khác.
     */
    private void settle(java.util.Optional<String> messageKey) {
        panel.setStatus(text(messageKey.orElse("trade.error.unknown")));
        sender.getTradeStatus();
    }

    /**
     * Xác nhận đã có kết luận. Xoá khoá idempotency dù thành hay bại (xem
     * {@link TradeController#onConfirmSettled}); phiên thi hành xong sẽ tự đóng qua
     * {@code TradeClosedEvent} của server, client không tự dọn trước.
     */
    private void onConfirmSettled(ConfirmTradeResponse response) {
        pendingConfirmSequenceId = NO_PENDING_CONFIRM;
        controller.onConfirmSettled();
        settle(TradeController.confirmMessageKey(response.getOutcome()));
    }

    /**
     * Server từ chối lệnh Xác nhận đang chờ bằng {@code ErrorResponse} — đối phương vừa đổi lời chào
     * ({@code error.trade_offer_just_changed}, README dòng 25(i)(b)). Chỉ nhận đúng gói khớp
     * {@code sequenceId} của lệnh đó, cùng luật {@code GuildCoordinator#onCreateRejected}. Hỏi lại
     * trạng thái ({@link #settle}) chính là thứ người chơi cần: lời chào mới hiện ra trước khi họ bấm lại.
     */
    private boolean onConfirmRejected(ErrorResponse error) {
        if (pendingConfirmSequenceId == NO_PENDING_CONFIRM
                || error.getRelatedSequenceId() != pendingConfirmSequenceId) {
            return false;
        }
        pendingConfirmSequenceId = NO_PENDING_CONFIRM;
        controller.onConfirmSettled();
        settle(TradeController.confirmErrorMessageKey(error.getErrorKey()));
        return true;
    }

    private void onInvited(TradeRequestedEvent event) {
        controller.onInvited(event);
        // MỞ cửa sổ: lời mời giao dịch tới lúc người chơi đang làm việc khác, và một lời mời không
        // ai thấy thì đối phương chỉ biết đứng chờ. Dùng open() (có hỏi lại trạng thái) là ĐÚNG ở
        // đây vì lời mời đến từ server chứ không phải từ một cú bấm của mình.
        panel.open();
        refreshView();
    }

    private void onClosed(TradeClosedEvent event) {
        controller.clearSession();
        panel.setStatus(text(
                event.getReason() == TradeCloseReason.TRADE_CLOSE_REASON_EXECUTED
                        ? "trade.notice.executed"
                        : "trade.notice.closed"));
        refreshView();
    }

    private void acceptInvite() {
        controller.invite().ifPresent(invite -> sender.acceptTradeRequest(invite.sessionId()));
    }

    /** Từ chối một lời mời — trade.proto dùng CHUNG {@code CancelTradeCommand} cho từ chối và huỷ. */
    private void declineInvite() {
        controller.invite().ifPresent(invite -> {
            sender.cancelTrade(invite.sessionId());
            controller.clearInvite();
            refreshView();
        });
    }

    private void setGold(String goldText) {
        if (!controller.inTrade()) {
            return;
        }
        TradeController.parseGold(goldText)
                .ifPresentOrElse(
                        gold -> sender.setTradeOffer(controller.sessionId(), gold, controller.offerWith(0L, false)),
                        () -> panel.setStatus(text("trade.error.bad_gold")));
    }

    /**
     * Đưa một món trong túi đồ vào lời chào — lối vào của nút Giao dịch trong giao diện túi đồ (nối
     * ở {@code WorldScreen#wireHud}), cùng khuôn nút Bán của cửa hàng.
     *
     * <p>Gửi lại TOÀN BỘ lời chào chứ không chỉ món mới: {@code SetTradeOfferCommand} thay hẳn lời
     * chào cũ, không cộng dồn (trade.proto).
     */
    void offerItem(long itemId) {
        if (!controller.inTrade()) {
            panel.setStatus(text("trade.error.not_in_trade"));
            return;
        }
        sender.setTradeOffer(controller.sessionId(), controller.myGoldOffer(), controller.offerWith(itemId, false));
    }

    private void confirm() {
        if (!controller.inTrade()) {
            return;
        }
        pendingConfirmSequenceId = sender.confirmTrade(
                controller.sessionId(), controller.beginConfirm(controller.sessionId()), controller.offerVersion());
    }

    private void cancel() {
        if (controller.inTrade()) {
            sender.cancelTrade(controller.sessionId());
        }
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống panel. Gọi sau MỖI lần server trả lời hoặc catalog đổi. */
    private void refreshView() {
        panel.setInvite(controller
                .invite()
                .map(invite -> format("trade.invite.from", invite.fromName()))
                .orElse(""));
        if (!controller.inTrade()) {
            panel.setSession("", "", "");
            panel.setItems(List.of(), "", true);
            panel.setItems(List.of(), "", false);
            return;
        }
        panel.setSession(
                format("trade.session.counterpart", controller.counterpartName(), phaseText()),
                offerText(controller.myGoldOffer(), controller.myConfirmed()),
                offerText(controller.theirGoldOffer(), controller.theirConfirmed()));
        pushItems(controller.myItems(), true);
        pushItems(controller.theirItems(), false);
    }

    private String phaseText() {
        return text(controller.phase() == TradePhase.TRADE_PHASE_OPEN ? "trade.phase.open" : "trade.phase.pending");
    }

    /** Vàng chào + cờ xác nhận đi CÙNG một dòng — xem javadoc {@link TradePanel} về lý do. */
    private String offerText(long gold, boolean confirmed) {
        return format("trade.offer.summary", gold, text(confirmed ? "trade.confirmed.yes" : "trade.confirmed.no"));
    }

    private void pushItems(List<TradeController.OfferItem> items, boolean mine) {
        itemRows.clear();
        int shown = Math.min(items.size(), ScrollingList.MAX_BUILT_ROWS);
        for (int index = 0; index < shown; index++) {
            itemRows.add(itemText(items.get(index)));
        }
        int hidden = items.size() - shown;
        panel.setItems(itemRows, hidden > 0 ? format("trade.items.truncated", hidden) : "", mine);
    }

    /**
     * Một dòng vật phẩm trong lời chào. {@code item_template_id == 0} là món đã biến mất giữa chừng
     * (tham chiếu mềm của trade.proto) — nói thẳng ra, xem javadoc lớp.
     */
    private String itemText(TradeController.OfferItem item) {
        if (item.itemTemplateId() == 0L) {
            return text("trade.item.gone");
        }
        String name = itemCatalog
                .nameKey(item.itemTemplateId())
                .map(this::text)
                .orElseGet(() -> format("trade.item.raw", item.itemTemplateId()));
        return item.enhanceLevel() > 0
                ? format("trade.item.enhanced", name, item.quantity(), item.enhanceLevel())
                : format("trade.item.plain", name, item.quantity());
    }

    private String text(String key) {
        return i18n.get(key);
    }

    private String format(String key, Object... arguments) {
        return i18n.format(key, arguments);
    }

    /** Rời màn hình/mất kết nối — phiên giao dịch của một lần chơi không sống sót sang lần sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("trade.hint.open"));
        refreshView();
    }
}
