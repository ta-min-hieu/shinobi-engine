package dev.kage.trade.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TransactionRunner;

class ConfirmTradeUseCaseTest {

    private static final TransactionRunner DIRECT_TX = action -> action.accept(null);

    private static ConfirmTradeUseCase newUseCase(TradePorts ports) {
        return new ConfirmTradeUseCase(ports, DIRECT_TX);
    }

    @Test
    void shouldReturnOk_whenOnlyOneSideConfirmed() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort,
                new FakeTradeInventoryPort(),
                new FakeTradeGoldPort(),
                new FakeTradeAuditPort(),
                new FakeIdempotencyPort()));

        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 1, "key-1");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OK);
        assertThat(sessionPort.findActiveSessionOfCharacter(1)).isPresent(); // chưa thi hành
    }

    @Test
    void shouldExecuteAndSwapGoldAndItems_whenBothConfirmed() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        FakeTradeGoldPort goldPort = new FakeTradeGoldPort();
        FakeTradeAuditPort auditPort = new FakeTradeAuditPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        goldPort.setGold(1, 100);
        goldPort.setGold(5, 50);
        long requesterItemId = inventoryPort.addItem(1, 200, 3, 0);
        long targetItemId = inventoryPort.addItem(5, 300, 1, 2);
        sessionPort.replaceOffer(session.id(), 1, 20, List.of(new OfferedItemInput(requesterItemId, 3)));
        sessionPort.replaceOffer(session.id(), 5, 10, List.of(new OfferedItemInput(targetItemId, 1)));
        ConfirmTradeUseCase useCase =
                newUseCase(new TradePorts(sessionPort, inventoryPort, goldPort, auditPort, new FakeIdempotencyPort()));

        useCase.execute(session.id(), 1, "key-a");
        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 5, "key-b");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.EXECUTED);
        assertThat(goldPort.getGold(1)).isEqualTo(90); // 100 - 20 + 10
        assertThat(goldPort.getGold(5)).isEqualTo(60); // 50 - 10 + 20
        assertThat(inventoryPort.findItemById(requesterItemId).orElseThrow().characterId())
                .isEqualTo(5);
        assertThat(inventoryPort.findItemById(targetItemId).orElseThrow().characterId())
                .isEqualTo(1);
        assertThat(sessionPort.findActiveSessionOfCharacter(1)).isEmpty(); // phiên đã bị xoá
        assertThat(auditPort.loggedActions).containsExactly("TRADE_EXECUTED", "TRADE_EXECUTED");
    }

    /**
     * Phiên và lời chào bị XOÁ ngay khi thi hành xong (V16, CASCADE) — nên dòng {@code audit_log} là
     * dấu vết DUY NHẤT còn lại của giao dịch. Trước bản vá (2026-10-03) nó chỉ có {@code sessionId}
     * + {@code counterpartId}: không ai trả lời được "giao dịch đó chuyển gì", đúng câu hỏi điều tra
     * gian lận mà DATABASE.md §2.7 bắt buộc audit để trả lời.
     */
    @Test
    void shouldAuditWhatEachSideGaveAndReceived_whenExecuted() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        FakeTradeGoldPort goldPort = new FakeTradeGoldPort();
        FakeTradeAuditPort auditPort = new FakeTradeAuditPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        goldPort.setGold(1, 100);
        long requesterItemId = inventoryPort.addItem(1, 200, 3, 0);
        sessionPort.replaceOffer(session.id(), 1, 20, List.of(new OfferedItemInput(requesterItemId, 2)));
        sessionPort.replaceOffer(session.id(), 5, 0, List.of());
        ConfirmTradeUseCase useCase =
                newUseCase(new TradePorts(sessionPort, inventoryPort, goldPort, auditPort, new FakeIdempotencyPort()));

        useCase.execute(session.id(), 1, "key-a");
        useCase.execute(session.id(), 5, "key-b");

        String item = "{\"itemId\":" + requesterItemId + ",\"quantity\":2}";
        assertThat(auditPort.loggedDetails)
                .containsExactly(
                        "{\"sessionId\":" + session.id() + ",\"counterpartId\":5,\"goldGiven\":20,"
                                + "\"goldReceived\":0,\"itemsGiven\":[" + item + "],\"itemsReceived\":[]}",
                        "{\"sessionId\":" + session.id() + ",\"counterpartId\":1,\"goldGiven\":0,"
                                + "\"goldReceived\":20,\"itemsGiven\":[],\"itemsReceived\":[" + item + "]}");
    }

    @Test
    void shouldReturnOfferInvalid_whenRecipientBagWouldEndUpOverCapacity() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        FakeTradeAuditPort auditPort = new FakeTradeAuditPort();
        inventoryPort.setCapacity(1);
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        long itemId = inventoryPort.addItem(1, 200, 1, 0);
        inventoryPort.addItem(5, 300, 1, 0); // túi bên 5 đã đủ 1/1
        sessionPort.replaceOffer(session.id(), 1, 0, List.of(new OfferedItemInput(itemId, 1)));
        sessionPort.replaceOffer(session.id(), 5, 0, List.of());
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort, inventoryPort, new FakeTradeGoldPort(), auditPort, new FakeIdempotencyPort()));

        useCase.execute(session.id(), 1, "key-a");
        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 5, "key-b");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
        assertThat(auditPort.loggedActions).isEmpty();
    }

    @Test
    void shouldSplitStack_whenOfferedQuantityLessThanRowQuantity() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        FakeTradeGoldPort goldPort = new FakeTradeGoldPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        long itemId = inventoryPort.addItem(1, 200, 10, 0); // có 10, chào 3
        sessionPort.replaceOffer(session.id(), 1, 0, List.of(new OfferedItemInput(itemId, 3)));
        sessionPort.replaceOffer(session.id(), 5, 0, List.of());
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort, inventoryPort, goldPort, new FakeTradeAuditPort(), new FakeIdempotencyPort()));

        useCase.execute(session.id(), 1, "key-a");
        useCase.execute(session.id(), 5, "key-b");

        assertThat(inventoryPort.findItemById(itemId).orElseThrow().quantity()).isEqualTo(7); // giữ lại 7
        assertThat(inventoryPort.findItemById(itemId).orElseThrow().characterId())
                .isEqualTo(1);
    }

    @Test
    void shouldReturnOfferInvalid_whenItemNoLongerOwnedAtExecution() {
        // Fake KHÔNG tự rollback effect đã gọi (đó là việc của JdbcTransactionRunner thật — Postgres
        // thật verify rollback ĐẦY ĐỦ ở TradeIntegrationTest, cùng cách BuyItemUseCaseTest chỉ verify
        // ĐÚNG control-flow: audit KHÔNG ghi, phiên KHÔNG bị xoá — không assert lại gold Fake).
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        FakeTradeInventoryPort inventoryPort = new FakeTradeInventoryPort();
        FakeTradeGoldPort goldPort = new FakeTradeGoldPort();
        FakeTradeAuditPort auditPort = new FakeTradeAuditPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        goldPort.setGold(1, 100);
        long itemId = inventoryPort.addItem(1, 200, 1, 0);
        sessionPort.replaceOffer(session.id(), 1, 20, List.of(new OfferedItemInput(itemId, 1)));
        sessionPort.replaceOffer(session.id(), 5, 0, List.of());
        newUseCase(new TradePorts(sessionPort, inventoryPort, goldPort, auditPort, new FakeIdempotencyPort()))
                .execute(session.id(), 5, "key-setup"); // bên 5 confirm trước
        // item đã bị bán/mất TRƯỚC KHI bên 1 confirm (mô phỏng bán ở shop giữa chừng)
        inventoryPort.transferItem(
                null, new dev.kage.trade.domain.TradeInventoryPort.TransferRequest(1, 999, itemId, 1));

        ConfirmTradeUseCase useCase =
                newUseCase(new TradePorts(sessionPort, inventoryPort, goldPort, auditPort, new FakeIdempotencyPort()));
        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 1, "key-final");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.OFFER_INVALID);
        assertThat(auditPort.loggedActions).isEmpty(); // KHÔNG ghi audit cho giao dịch bị rollback
        assertThat(sessionPort.findActiveSessionOfCharacter(1)).isPresent(); // phiên vẫn còn, chưa xoá
    }

    @Test
    void shouldReturnDuplicateRequest_whenIdempotencyKeyReused() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        sessionPort.acceptRequest(session.id(), 5);
        FakeIdempotencyPort idempotencyPort = new FakeIdempotencyPort();
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort,
                new FakeTradeInventoryPort(),
                new FakeTradeGoldPort(),
                new FakeTradeAuditPort(),
                idempotencyPort));
        useCase.execute(session.id(), 1, "same-key");

        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 1, "same-key");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.DUPLICATE_REQUEST);
    }

    @Test
    void shouldReturnNotFound_whenCallerNotParticipant() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort,
                new FakeTradeInventoryPort(),
                new FakeTradeGoldPort(),
                new FakeTradeAuditPort(),
                new FakeIdempotencyPort()));

        ConfirmTradeUseCase.Result result = useCase.execute(session.id(), 999, "key-1");

        assertThat(result).isEqualTo(ConfirmTradeUseCase.Result.NOT_FOUND);
    }

    /** README dòng 25(ii), chủ dự án chặn 2026-10-14: người được mời chưa nhận lời thì chưa có gì để xác nhận. */
    @Test
    void shouldReturnNotFoundAndNotConfirm_whenSessionIsStillPendingAccept() {
        FakeTradeSessionPort sessionPort = new FakeTradeSessionPort();
        TradeSession session = sessionPort.createRequest(1, 5);
        ConfirmTradeUseCase useCase = newUseCase(new TradePorts(
                sessionPort,
                new FakeTradeInventoryPort(),
                new FakeTradeGoldPort(),
                new FakeTradeAuditPort(),
                new FakeIdempotencyPort()));

        ConfirmTradeUseCase.Result requesterResult = useCase.execute(session.id(), 1, "key-1");
        ConfirmTradeUseCase.Result targetResult = useCase.execute(session.id(), 5, "key-2");

        assertThat(requesterResult).isEqualTo(ConfirmTradeUseCase.Result.NOT_FOUND);
        assertThat(targetResult).isEqualTo(ConfirmTradeUseCase.Result.NOT_FOUND);
        assertThat(sessionPort.findActiveSessionOfCharacter(1))
                .as("phiên vẫn còn, chưa thi hành")
                .isPresent();
    }
}
