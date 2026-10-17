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

    public TradeFacade(TradeUseCases useCases) {
        this.useCases = useCases;
    }

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

    public AcceptResult acceptRequest(long sessionId, long characterId) {
        return switch (useCases.acceptTradeRequestUseCase().execute(sessionId, characterId)) {
            case dev.kage.trade.application.AcceptTradeRequestUseCase.Result.OK -> AcceptResult.OK;
            case dev.kage.trade.application.AcceptTradeRequestUseCase.Result.NOT_FOUND -> AcceptResult.NOT_FOUND;
        };
    }

    public CancelResult cancel(long sessionId, long characterId) {
        return switch (useCases.cancelTradeUseCase().execute(sessionId, characterId)) {
            case dev.kage.trade.application.CancelTradeUseCase.Result.OK -> CancelResult.OK;
            case dev.kage.trade.application.CancelTradeUseCase.Result.NOT_FOUND -> CancelResult.NOT_FOUND;
        };
    }

    public SetOfferResult setOffer(long sessionId, long characterId, long goldAmount, List<OfferItemInput> items) {
        List<OfferedItemInput> domainItems = items.stream()
                .map(i -> new OfferedItemInput(i.itemId(), i.quantity()))
                .toList();
        return switch (useCases.setTradeOfferUseCase().execute(sessionId, characterId, goldAmount, domainItems)) {
            case dev.kage.trade.application.SetTradeOfferUseCase.Result.OK -> SetOfferResult.OK;
            case dev.kage.trade.application.SetTradeOfferUseCase.Result.NOT_FOUND -> SetOfferResult.NOT_FOUND;
        };
    }

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

    public enum RequestOutcome {
        OK,
        CANNOT_TRADE_SELF,
        ALREADY_IN_TRADE,
        TARGET_ALREADY_IN_TRADE
    }

    public enum AcceptResult {
        OK,
        NOT_FOUND
    }

    public enum CancelResult {
        OK,
        NOT_FOUND
    }

    public enum SetOfferResult {
        OK,
        NOT_FOUND
    }

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

    public enum TradePhase {
        PENDING_ACCEPT,
        OPEN
    }

    public record RequestResult(RequestOutcome outcome, Long sessionId) {}

    public record OfferItemInput(long itemId, int quantity) {}

    public record OfferItemView(long itemId, long itemTemplateId, int quantity, int enhanceLevel) {}

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
        public TradeStatusView {
            myItems = List.copyOf(myItems);
            theirItems = List.copyOf(theirItems);
        }
    }
}
