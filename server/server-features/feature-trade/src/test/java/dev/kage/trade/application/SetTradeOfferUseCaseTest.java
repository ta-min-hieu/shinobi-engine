package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.trade.domain.OfferedItemInput;
import java.util.List;
import org.junit.jupiter.api.Test;

class SetTradeOfferUseCaseTest {

    @Test
    void shouldSetOffer_whenSessionOpenAndCallerIsParticipant() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        var useCase = new SetTradeOfferUseCase(port);

        var result = useCase.execute(session.id(), 1, 100, List.of(new OfferedItemInput(42, 3)));

        assertThat(result).isEqualTo(SetTradeOfferUseCase.Result.OK);
        var snapshot = port.findSnapshot(session.id()).orElseThrow();
        assertThat(snapshot.requesterGold()).isEqualTo(100);
        assertThat(snapshot.items()).hasSize(1);
    }

    @Test
    void shouldReturnNotFound_whenSessionStillPendingAccept() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        var useCase = new SetTradeOfferUseCase(port);

        var result = useCase.execute(session.id(), 1, 0, List.of());

        assertThat(result).isEqualTo(SetTradeOfferUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldResetBothConfirmedFlags_whenOfferChanges() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        port.replaceOffer(session.id(), 1, 0, List.of());
        port.replaceOffer(session.id(), 5, 0, List.of());
        port.setConfirmed(null, session.id(), 1);
        port.setConfirmed(null, session.id(), 5);
        var useCase = new SetTradeOfferUseCase(port);

        useCase.execute(session.id(), 5, 50, List.of()); // bên 5 đổi ý lời chào sau khi cả 2 đã confirm

        var snapshot = port.findSnapshot(session.id()).orElseThrow();
        assertThat(snapshot.requesterConfirmed()).isFalse();
        assertThat(snapshot.targetConfirmed()).isFalse();
    }

    @Test
    void shouldRejectNegativeGold() {
        var port = new FakeTradeSessionPort();
        var session = port.createRequest(1, 5);
        port.acceptRequest(session.id(), 5);
        var useCase = new SetTradeOfferUseCase(port);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> useCase.execute(session.id(), 1, -1, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
