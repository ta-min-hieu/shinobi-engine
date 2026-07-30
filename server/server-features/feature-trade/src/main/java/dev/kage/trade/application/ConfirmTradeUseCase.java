package dev.kage.trade.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.trade.domain.IdempotencyKeyReusedException;
import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.TradeInventoryPort.TransferOutcome;
import dev.kage.trade.domain.TradeOfferItem;
import dev.kage.trade.domain.TradeSessionSnapshot;
import dev.kage.trade.domain.Transaction;
import dev.kage.trade.domain.TransactionRunner;

/**
 * Xác nhận trade — TRONG CÙNG 1 transaction: ghi {@code idempotencyKey} (ADR-025) → khoá phiên
 * FOR UPDATE → đánh dấu confirmed → nếu CẢ 2 bên đã confirm, xác thực lại TOÀN BỘ lời chào (còn
 * đúng chủ + đủ số lượng/gold không, {@link TradeInventoryPort#transferItem}/{@link
 * TradeGoldPort#deductGold}) rồi chuyển gold/item + ghi audit + xoá phiên. Nếu xác thực lại thất
 * bại, ném {@link TradeExecutionAbortedException} để rollback TOÀN BỘ (kể cả cờ confirmed vừa
 * set) — đây CHÍNH XÁC là bước "khoá item khi đang trade" GAMEPLAY.md §8 yêu cầu, thực hiện bằng
 * xác thực lại lúc thi hành thay vì khoá trước (feature-trade/CLAUDE.md).
 */
public final class ConfirmTradeUseCase {

    private final TradePorts ports;
    private final TransactionRunner tx;

    public ConfirmTradeUseCase(TradePorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    public Result execute(long sessionId, long characterId, String idempotencyKey) {
        AtomicReference<Result> resultHolder = new AtomicReference<Result>();
        try {
            tx.inTransaction((Transaction t) -> {
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                Optional<TradeSessionSnapshot> snapshotOpt = ports.sessionPort().lockForExecution(t, sessionId);
                if (snapshotOpt.isEmpty() || !snapshotOpt.get().involves(characterId)) {
                    resultHolder.set(Result.NOT_FOUND);
                    return;
                }
                TradeSessionSnapshot snapshot = snapshotOpt.get();
                ports.sessionPort().setConfirmed(t, sessionId, characterId);
                boolean requesterConfirmed =
                        snapshot.requesterConfirmed() || characterId == snapshot.requesterCharacterId();
                boolean targetConfirmed = snapshot.targetConfirmed() || characterId == snapshot.targetCharacterId();
                if (!(requesterConfirmed && targetConfirmed)) {
                    resultHolder.set(Result.OK);
                    return;
                }
                executeTransferOrAbort(t, snapshot);
                logAudit(t, snapshot);
                ports.sessionPort().deleteSession(t, sessionId);
                resultHolder.set(Result.EXECUTED);
            });
        } catch (IdempotencyKeyReusedException e) {
            return Result.DUPLICATE_REQUEST;
        } catch (TradeExecutionAbortedException e) {
            return Result.OFFER_INVALID;
        }
        return resultHolder.get();
    }

    private void executeTransferOrAbort(Transaction t, TradeSessionSnapshot snapshot) {
        long requesterId = snapshot.requesterCharacterId();
        long targetId = snapshot.targetCharacterId();
        if (snapshot.requesterGold() > 0 && !ports.goldPort().deductGold(t, requesterId, snapshot.requesterGold())) {
            throw new TradeExecutionAbortedException();
        }
        if (snapshot.targetGold() > 0 && !ports.goldPort().deductGold(t, targetId, snapshot.targetGold())) {
            throw new TradeExecutionAbortedException();
        }
        if (snapshot.requesterGold() > 0) {
            ports.goldPort().creditGold(t, targetId, snapshot.requesterGold());
        }
        if (snapshot.targetGold() > 0) {
            ports.goldPort().creditGold(t, requesterId, snapshot.targetGold());
        }
        for (TradeOfferItem item : snapshot.itemsOffered(requesterId)) {
            transferOrAbort(
                    t, new TradeInventoryPort.TransferRequest(requesterId, targetId, item.itemId(), item.quantity()));
        }
        for (TradeOfferItem item : snapshot.itemsOffered(targetId)) {
            transferOrAbort(
                    t, new TradeInventoryPort.TransferRequest(targetId, requesterId, item.itemId(), item.quantity()));
        }
    }

    private void transferOrAbort(Transaction t, TradeInventoryPort.TransferRequest request) {
        TransferOutcome outcome = ports.inventoryPort().transferItem(t, request);
        if (outcome != TransferOutcome.OK) {
            throw new TradeExecutionAbortedException();
        }
    }

    private void logAudit(Transaction t, TradeSessionSnapshot snapshot) {
        String detail = "{\"sessionId\":%d,\"counterpartId\":%d}";
        ports.auditPort()
                .log(
                        t,
                        snapshot.requesterCharacterId(),
                        "TRADE_EXECUTED",
                        detail.formatted(snapshot.id(), snapshot.targetCharacterId()));
        ports.auditPort()
                .log(
                        t,
                        snapshot.targetCharacterId(),
                        "TRADE_EXECUTED",
                        detail.formatted(snapshot.id(), snapshot.requesterCharacterId()));
    }

    public enum Result {
        OK,
        EXECUTED,
        NOT_FOUND,
        OFFER_INVALID,
        DUPLICATE_REQUEST
    }
}
