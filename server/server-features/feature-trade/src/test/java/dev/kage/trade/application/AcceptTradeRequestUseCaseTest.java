package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AcceptTradeRequestUseCaseTest {

    @Test
    void shouldAccept_whenCallerIsTarget() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        var useCase = new AcceptTradeRequestUseCase(port);

        var result = useCase.execute(session.id(), 5);

        assertThat(result).isEqualTo(AcceptTradeRequestUseCase.Result.OK);
    }

    @Test
    void shouldReject_whenCallerIsRequester() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        var useCase = new AcceptTradeRequestUseCase(port);

        var result = useCase.execute(session.id(), 1);

        assertThat(result).isEqualTo(AcceptTradeRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnNotFound_whenSessionDoesNotExist() {
        var useCase = new AcceptTradeRequestUseCase(new FakeTradeSessionPort());

        assertThat(useCase.execute(999, 5)).isEqualTo(AcceptTradeRequestUseCase.Result.NOT_FOUND);
    }
}
