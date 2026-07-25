package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.trade.domain.TradeSession;
import org.junit.jupiter.api.Test;

class AcceptTradeRequestUseCaseTest {

    @Test
    void shouldAccept_whenCallerIsTarget() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        AcceptTradeRequestUseCase useCase = new AcceptTradeRequestUseCase(port);

        AcceptTradeRequestUseCase.Result result = useCase.execute(session.id(), 5);

        assertThat(result).isEqualTo(AcceptTradeRequestUseCase.Result.OK);
    }

    @Test
    void shouldReject_whenCallerIsRequester() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        AcceptTradeRequestUseCase useCase = new AcceptTradeRequestUseCase(port);

        AcceptTradeRequestUseCase.Result result = useCase.execute(session.id(), 1);

        assertThat(result).isEqualTo(AcceptTradeRequestUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnNotFound_whenSessionDoesNotExist() {
        AcceptTradeRequestUseCase useCase = new AcceptTradeRequestUseCase(new FakeTradeSessionPort());

        assertThat(useCase.execute(999, 5)).isEqualTo(AcceptTradeRequestUseCase.Result.NOT_FOUND);
    }
}
