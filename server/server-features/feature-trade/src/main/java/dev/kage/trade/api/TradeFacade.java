package dev.kage.trade.api;

import java.util.List;
import java.util.Optional;

import dev.kage.trade.application.ConfirmTradeUseCase;
import dev.kage.trade.application.RequestTradeUseCase;
import dev.kage.trade.application.TradeUseCases;
import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeStatus;

/**
 * API công khai của {@code feature-trade} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code trade_session}/{@code trade_offer_item} trực tiếp (Điều 24). Tên nhân
 * vật/trạng thái online KHÔNG thuộc facade này — cùng quyết định feature-friend/guild/party.
 */
public final class TradeFacade {

    private final TradeUseCases useCases;

    /** Dựng ở composition root. */
    public TradeFacade(TradeUseCases useCases) {
        this.useCases = useCases;
    }

    /** Mời {@code targetCharacterId} giao dịch — tạo phiên ở trạng thái chờ chấp nhận. */
    public RequestResult requestTrade(long characterId, long targetCharacterId) {
        RequestTradeUseCase.Result result = useCases.requestTradeUseCase().execute(characterId, targetCharacterId);
        RequestOutcome outcome =
                switch (result.outcome()) {
                    case OK -> RequestOutcome.OK;
                    case CANNOT_TRADE_SELF -> RequestOutcome.CANNOT_TRADE_SELF;
                    case ALREADY_IN_TRADE -> RequestOutcome.ALREADY_IN_TRADE;
                    case TARGET_ALREADY_IN_TRADE -> RequestOutcome.TARGET_ALREADY_IN_TRADE;
                };
        Long sessionId = result.session() == null ? null : result.session().id();
        return new RequestResult(outcome, sessionId);
    }

    /** Người được mời chấp nhận — phiên chuyển sang mở, hai bên đặt lời chào được. */
    public AcceptResult acceptRequest(long sessionId, long characterId) {
        return switch (useCases.acceptTradeRequestUseCase().execute(sessionId, characterId)) {
            case dev.kage.trade.application.AcceptTradeRequestUseCase.Result.OK -> AcceptResult.OK;
            case dev.kage.trade.application.AcceptTradeRequestUseCase.Result.NOT_FOUND -> AcceptResult.NOT_FOUND;
        };
    }

    /** Một trong hai bên huỷ/từ chối — xoá phiên, không có gì để hoàn trả (không escrow). */
    public CancelResult cancel(long sessionId, long characterId) {
        return switch (useCases.cancelTradeUseCase().execute(sessionId, characterId)) {
            case dev.kage.trade.application.CancelTradeUseCase.Result.OK -> CancelResult.OK;
            case dev.kage.trade.application.CancelTradeUseCase.Result.NOT_FOUND -> CancelResult.NOT_FOUND;
        };
    }

    /**
     * Thay TOÀN BỘ lời chào của {@code characterId} — reset cờ xác nhận của cả hai bên. Chỉ lưu ý định:
     * sở hữu được xác thực lại lúc thi hành ({@link #confirm}).
     */
    public SetOfferResult setOffer(long sessionId, long characterId, long goldAmount, List<OfferItemInput> items) {
        List<OfferedItemInput> domainItems = items.stream()
                .map(i -> new OfferedItemInput(i.itemId(), i.quantity()))
                .toList();
        return switch (useCases.setTradeOfferUseCase().execute(sessionId, characterId, goldAmount, domainItems)) {
            case dev.kage.trade.application.SetTradeOfferUseCase.Result.OK -> SetOfferResult.OK;
            case dev.kage.trade.application.SetTradeOfferUseCase.Result.NOT_FOUND -> SetOfferResult.NOT_FOUND;
        };
    }

    /**
     * Xác nhận lời chào hiện tại; khi CẢ HAI đã xác nhận thì thi hành nguyên tử (xác thực lại sở hữu,
     * chuyển vàng/vật phẩm, ghi audit) — xem {@code ConfirmTradeUseCase}.
     */
    public ConfirmResult confirm(long sessionId, long characterId, String idempotencyKey) {
        return switch (useCases.confirmTradeUseCase().execute(sessionId, characterId, idempotencyKey)) {
            case ConfirmTradeUseCase.Result.OK -> ConfirmResult.OK;
            case ConfirmTradeUseCase.Result.EXECUTED -> ConfirmResult.EXECUTED;
            case ConfirmTradeUseCase.Result.NOT_FOUND -> ConfirmResult.NOT_FOUND;
            case ConfirmTradeUseCase.Result.OFFER_INVALID -> ConfirmResult.OFFER_INVALID;
            case ConfirmTradeUseCase.Result.DUPLICATE_REQUEST -> ConfirmResult.DUPLICATE_REQUEST;
            case ConfirmTradeUseCase.Result.OFFER_JUST_CHANGED -> ConfirmResult.OFFER_JUST_CHANGED;
        };
    }

    /** Phiên đang hoạt động của {@code characterId}, nhìn từ phía họ — rỗng nếu không ở phiên nào. */
    public Optional<TradeStatusView> getStatus(long characterId) {
        return useCases.getTradeStatusUseCase().execute(characterId).map(r -> {
            boolean isRequester = r.requesterCharacterId() == characterId;
            long counterpartId = isRequester ? r.targetCharacterId() : r.requesterCharacterId();
            long myGold = isRequester ? r.requesterGold() : r.targetGold();
            long theirGold = isRequester ? r.targetGold() : r.requesterGold();
            boolean myConfirmed = isRequester ? r.requesterConfirmed() : r.targetConfirmed();
            boolean theirConfirmed = isRequester ? r.targetConfirmed() : r.requesterConfirmed();
            List<OfferItemView> myItems = r.items().stream()
                    .filter(i -> i.offeringCharacterId() == characterId)
                    .map(i -> new OfferItemView(i.itemId(), i.itemTemplateId(), i.quantity(), i.enhanceLevel()))
                    .toList();
            List<OfferItemView> theirItems = r.items().stream()
                    .filter(i -> i.offeringCharacterId() != characterId)
                    .map(i -> new OfferItemView(i.itemId(), i.itemTemplateId(), i.quantity(), i.enhanceLevel()))
                    .toList();
            TradePhase phase = r.status() == TradeStatus.PENDING_ACCEPT ? TradePhase.PENDING_ACCEPT : TradePhase.OPEN;
            return new TradeStatusView(
                    r.sessionId(),
                    phase,
                    counterpartId,
                    isRequester,
                    myGold,
                    myItems,
                    theirGold,
                    theirItems,
                    myConfirmed,
                    theirConfirmed);
        });
    }

    /** Kết quả mời giao dịch. */
    public enum RequestOutcome {
        OK,
        CANNOT_TRADE_SELF,
        ALREADY_IN_TRADE,
        TARGET_ALREADY_IN_TRADE
    }

    /** Kết quả chấp nhận lời mời. */
    public enum AcceptResult {
        OK,
        NOT_FOUND
    }

    /** Kết quả huỷ phiên. */
    public enum CancelResult {
        OK,
        NOT_FOUND
    }

    /** Kết quả đặt lời chào. */
    public enum SetOfferResult {
        OK,
        NOT_FOUND
    }

    /** Kết quả xác nhận — {@code OK} là đã xác nhận và đang chờ đối phương, {@code EXECUTED} là đã thi hành. */
    public enum ConfirmResult {
        OK,
        EXECUTED,
        NOT_FOUND,
        OFFER_INVALID,
        DUPLICATE_REQUEST,
        /**
         * Đối phương vừa sửa lời chào (≤ 3 giây) — không xác nhận gì; không có outcome trên dây, gateway
         * trả {@code ErrorResponse} {@code error.trade_offer_just_changed} (README dòng 25(i)(b)).
         */
        OFFER_JUST_CHANGED
    }

    /** Giai đoạn của phiên. */
    public enum TradePhase {
        PENDING_ACCEPT,
        OPEN
    }

    /** Kết quả mời — {@code sessionId} là {@code null} khi {@code outcome != OK}. */
    public record RequestResult(RequestOutcome outcome, Long sessionId) {}

    /** Một dòng lời chào: {@code quantity} của dòng túi đồ {@code itemId} (có thể là một phần chồng). */
    public record OfferItemInput(long itemId, int quantity) {}

    /** Một dòng lời chào để hiển thị — {@code itemTemplateId == 0}: món đã không còn thuộc người chào. */
    public record OfferItemView(long itemId, long itemTemplateId, int quantity, int enhanceLevel) {}

    /** Trạng thái phiên nhìn từ phía người hỏi ({@code my*}) và đối phương ({@code their*}). */
    public record TradeStatusView(
            long sessionId,
            TradePhase phase,
            long counterpartCharacterId,
            boolean isRequester,
            long myGoldOffer,
            List<OfferItemView> myItems,
            long theirGoldOffer,
            List<OfferItemView> theirItems,
            boolean myConfirmed,
            boolean theirConfirmed) {
        /** Chép phòng thủ hai danh sách lời chào. */
        public TradeStatusView {
            myItems = List.copyOf(myItems);
            theirItems = List.copyOf(theirItems);
        }
    }
}
