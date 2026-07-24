package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RequestTradeUseCaseTest {

    @Test
    void shouldCreatePendingRequest() {
        var port = new FakeTradeSessionPort();
        var useCase = new RequestTradeUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.OK);
        assertThat(port.findActiveSessionOfCharacter(1)).isPresent();
        assertThat(port.findActiveSessionOfCharacter(5)).isPresent();
    }

    @Test
    void shouldRejectSelfTrade() {
        var useCase = new RequestTradeUseCase(new FakeTradeSessionPort());

        var result = useCase.execute(1, 1);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.CANNOT_TRADE_SELF);
    }

    @Test
    void shouldReject_whenRequesterAlreadyInTrade() {
        var port = new FakeTradeSessionPort();
        port.createRequest(1, 5);
        var useCase = new RequestTradeUseCase(port);

        var result = useCase.execute(1, 7);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.ALREADY_IN_TRADE);
    }

    @Test
    void shouldReject_whenTargetAlreadyInTrade() {
        var port = new FakeTradeSessionPort();
        port.createRequest(5, 7);
        var useCase = new RequestTradeUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result.outcome()).isEqualTo(RequestTradeUseCase.Outcome.TARGET_ALREADY_IN_TRADE);
    }
}
