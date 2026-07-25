package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class GetTradeStatusUseCaseTest {

    @Test
    void shouldReturnSnapshot_forActiveSession() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        long itemId = inventoryPort.addItem(1, 200, 2, 3);
        sessionPort.replaceOffer(session.id(), 1, 10, List.of(new OfferedItemInput(itemId, 2)));
        GetTradeStatusUseCase useCase = new GetTradeStatusUseCase(sessionPort, inventoryPort);

        GetTradeStatusUseCase.Result result = useCase.execute(1).orElseThrow();

        assertThat(result.status()).isEqualTo(TradeStatus.OPEN);
        assertThat(result.requesterGold()).isEqualTo(10);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).itemTemplateId()).isEqualTo(200);
        assertThat(result.items().get(0).enhanceLevel()).isEqualTo(3);
    }

    @Test
    void shouldReturnEmpty_whenNoActiveSession() {
        GetTradeStatusUseCase useCase =
                new GetTradeStatusUseCase(new FakeTradeSessionPort(), new FakeTradeInventoryPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
