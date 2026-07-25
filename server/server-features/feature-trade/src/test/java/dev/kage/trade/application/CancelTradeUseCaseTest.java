package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.trade.domain.TradeSession;
import org.junit.jupiter.api.Test;

class CancelTradeUseCaseTest {

    @Test
    void shouldCancel_whilePendingAccept() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        CancelTradeUseCase useCase = new CancelTradeUseCase(port);

        CancelTradeUseCase.Result result = useCase.execute(session.id(), 5); // target từ chối

        assertThat(result).isEqualTo(CancelTradeUseCase.Result.OK);
        assertThat(port.findActiveSessionOfCharacter(1)).isEmpty();
    }

    @Test
    void shouldCancel_whileOpen() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        CancelTradeUseCase useCase = new CancelTradeUseCase(port);

        CancelTradeUseCase.Result result = useCase.execute(session.id(), 1); // requester huỷ giữa chừng

        assertThat(result).isEqualTo(CancelTradeUseCase.Result.OK);
        assertThat(port.findActiveSessionOfCharacter(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenCallerNotParticipant() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        CancelTradeUseCase useCase = new CancelTradeUseCase(port);

        CancelTradeUseCase.Result result = useCase.execute(session.id(), 999);

        assertThat(result).isEqualTo(CancelTradeUseCase.Result.NOT_FOUND);
        assertThat(port.findActiveSessionOfCharacter(1)).isPresent();
    }
}
