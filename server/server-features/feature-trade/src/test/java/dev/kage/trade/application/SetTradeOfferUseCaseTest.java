package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionSnapshot;

class SetTradeOfferUseCaseTest {

    @Test
    void shouldSetOffer_whenSessionOpenAndCallerIsParticipant() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        SetTradeOfferUseCase useCase = new SetTradeOfferUseCase(port);

        SetTradeOfferUseCase.Result result =
                useCase.execute(session.id(), 1, 100, List.of(new OfferedItemInput(42, 3)));

        assertThat(result).isEqualTo(SetTradeOfferUseCase.Result.OK);
        TradeSessionSnapshot snapshot = port.findSnapshot(session.id()).orElseThrow();
        assertThat(snapshot.requesterGold()).isEqualTo(100);
        assertThat(snapshot.items()).hasSize(1);
    }

    @Test
    void shouldReturnNotFound_whenSessionStillPendingAccept() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        SetTradeOfferUseCase useCase = new SetTradeOfferUseCase(port);

        SetTradeOfferUseCase.Result result = useCase.execute(session.id(), 1, 0, List.of());

        assertThat(result).isEqualTo(SetTradeOfferUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldResetBothConfirmedFlags_whenOfferChanges() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        port.replaceOffer(session.id(), 1, 0, List.of());
        port.replaceOffer(session.id(), 5, 0, List.of());
        port.setConfirmed(null, session.id(), 1);
        port.setConfirmed(null, session.id(), 5);
        SetTradeOfferUseCase useCase = new SetTradeOfferUseCase(port);

        useCase.execute(session.id(), 5, 50, List.of()); // bên 5 đổi ý lời chào sau khi cả 2 đã confirm

        TradeSessionSnapshot snapshot = port.findSnapshot(session.id()).orElseThrow();
        assertThat(snapshot.requesterConfirmed()).isFalse();
        assertThat(snapshot.targetConfirmed()).isFalse();
    }

    @Test
    void shouldRejectNegativeGold() {
        FakeTradeSessionPort port = new FakeTradeSessionPort();
        TradeSession session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        SetTradeOfferUseCase useCase = new SetTradeOfferUseCase(port);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(session.id(), 1, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
