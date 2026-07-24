package dev.kage.trade.application;

/**
 * Gộp toàn bộ UseCase mà {@link dev.kage.trade.api.TradeFacade} cần — tránh constructor Facade
 * vượt 4 tham số (Checkstyle ParameterNumber), cùng tinh thần {@code GuildUseCases}/{@code
 * PartyUseCases}.
 */
public record TradeUseCases(
        RequestTradeUseCase requestTradeUseCase,
        AcceptTradeRequestUseCase acceptTradeRequestUseCase,
        CancelTradeUseCase cancelTradeUseCase,
        SetTradeOfferUseCase setTradeOfferUseCase,
        ConfirmTradeUseCase confirmTradeUseCase,
        GetTradeStatusUseCase getTradeStatusUseCase) {
}
