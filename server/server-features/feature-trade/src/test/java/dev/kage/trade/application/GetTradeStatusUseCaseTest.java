package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeStatus;

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

    /**
     * Lời chào là tham chiếu MỀM, không xác thực sở hữu lúc chào — nên người chơi chào được
     * {@code itemId} của BẤT KỲ AI. Trước bản vá, màn trạng thái tra món đó bất kể chủ là ai: người
     * chào dò được loại + cấp cường hoá của túi người lạ theo id, và đối phương thấy "+15" trong
     * lời chào dù món đó không bao giờ chuyển được. Món không thuộc người chào phải hiện như món đã
     * biến mất (template 0) — cùng cách hiện món đã bán.
     */
    @Test
    void shouldNotRevealItem_whenOfferedItemBelongsToSomeoneElse() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        long strangersItemId = inventoryPort.addItem(99, 200, 1, 15);
        sessionPort.replaceOffer(session.id(), 1, 0, List.of(new OfferedItemInput(strangersItemId, 1)));
        GetTradeStatusUseCase useCase = new GetTradeStatusUseCase(sessionPort, inventoryPort);

        GetTradeStatusUseCase.Result result = useCase.execute(5).orElseThrow();

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).itemTemplateId()).isZero();
        assertThat(result.items().get(0).enhanceLevel()).isZero();
    }

    @Test
    void shouldReturnEmpty_whenNoActiveSession() {
        GetTradeStatusUseCase useCase =
                new GetTradeStatusUseCase(new FakeTradeSessionPort(), new FakeTradeInventoryPort());

        assertThat(useCase.execute(1)).isEmpty();
    }
}
