package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestTradeUseCaseTest {

    @Test
    void shouldCreatePendingRequest() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        RequestTradeUseCase useCase = new RequestTradeUseCase(port);

        RequestTradeUseCase.Result result = useCase.execute(1, 5);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.OK);
        assertThat(port.findActiveSessionOfCharacter(1)).isPresent();
        assertThat(port.findActiveSessionOfCharacter(5)).isPresent();
    }

    @Test
    void shouldRejectSelfTrade() {
        RequestTradeUseCase useCase = new RequestTradeUseCase(new FakeTradeSessionPort());

        RequestTradeUseCase.Result result = useCase.execute(1, 1);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.CANNOT_TRADE_SELF);
    }

    @Test
    void shouldReject_whenRequesterAlreadyInTrade() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        port.createRequest(1, 5);
        RequestTradeUseCase useCase = new RequestTradeUseCase(port);

        RequestTradeUseCase.Result result = useCase.execute(1, 7);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.ALREADY_IN_TRADE);
    }

    @Test
    void shouldReject_whenTargetAlreadyInTrade() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        port.createRequest(5, 7);
        RequestTradeUseCase useCase = new RequestTradeUseCase(port);

        RequestTradeUseCase.Result result = useCase.execute(1, 5);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.TARGET_ALREADY_IN_TRADE);
    }
}
