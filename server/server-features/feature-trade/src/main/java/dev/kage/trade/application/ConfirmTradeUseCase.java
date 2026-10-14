package dev.kage.trade.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.trade.domain.IdempotencyKeyReusedException;
import dev.kage.trade.domain.OwnedItemSnapshot;
import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.TradeInventoryPort.TransferOutcome;
import dev.kage.trade.domain.TradeOfferItem;
import dev.kage.trade.domain.TradeSessionSnapshot;
import dev.kage.trade.domain.TradeStatus;
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
                // Phiên còn PENDING_ACCEPT (người được mời chưa nhận lời) thì chưa có gì để xác nhận —
                // chủ dự án chặn 2026-10-14 (README dòng 25(ii)); cùng outcome với "không có phiên đang
                // mở" để không đổi protocol. SetTradeOffer vốn đã đòi OPEN.
                if (snapshotOpt.isEmpty()
                        || !snapshotOpt.get().involves(characterId)
                        || snapshotOpt.get().status() != TradeStatus.OPEN) {
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
                Map<Long, OwnedItemSnapshot> offeredRows = executeTransferOrAbort(t, snapshot);
                logAudit(t, snapshot, offeredRows);
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

    /** @return dòng NGUỒN của từng món đã chào, đọc TRƯỚC khi chuyển — cho audit (README dòng 25(iii)) */
    private Map<Long, OwnedItemSnapshot> executeTransferOrAbort(Transaction t, TradeSessionSnapshot snapshot) {
        long requesterId = snapshot.requesterCharacterId();
        long targetId = snapshot.targetCharacterId();
        // Khoá túi đồ CẢ HAI người trước vàng/vật phẩm — thứ tự khoá chung (DATABASE.md §5.1);
        // khoá phiên ở trên là ngoại lệ duy nhất được đứng trước, vì chỉ đọc phiên mới biết hai người.
        ports.inventoryPort().lockInventories(t, requesterId, targetId);
        Map<Long, OwnedItemSnapshot> offeredRows = new HashMap<>();
        for (TradeOfferItem item : snapshot.items()) {
            ports.inventoryPort().findItemById(t, item.itemId()).ifPresent(row -> offeredRows.put(item.itemId(), row));
        }
        transferGoldOrAbort(t, snapshot, requesterId, targetId);
        for (TradeOfferItem item : snapshot.itemsOffered(requesterId)) {
            transferOrAbort(
                    t, new TradeInventoryPort.TransferRequest(requesterId, targetId, item.itemId(), item.quantity()));
        }
        for (TradeOfferItem item : snapshot.itemsOffered(targetId)) {
            transferOrAbort(
                    t, new TradeInventoryPort.TransferRequest(targetId, requesterId, item.itemId(), item.quantity()));
        }
        // Sức chứa kiểm SAU mọi lượt chuyển — xem TradeInventoryPort#exceedsCapacity.
        if (ports.inventoryPort().exceedsCapacity(t, requesterId)
                || ports.inventoryPort().exceedsCapacity(t, targetId)) {
            throw new TradeExecutionAbortedException();
        }
        return offeredRows;
    }

    /** Tách khỏi {@link #executeTransferOrAbort} chỉ để giữ độ phức tạp method dưới ngưỡng
     * CyclomaticComplexity — thứ tự/điều kiện gốc (deduct cả 2 bên trước, rồi mới credit) giữ NGUYÊN. */
    private void transferGoldOrAbort(Transaction t, TradeSessionSnapshot snapshot, long requesterId, long targetId) {
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
    }

    private void transferOrAbort(Transaction t, TradeInventoryPort.TransferRequest request) {
        TransferOutcome outcome = ports.inventoryPort().transferItem(t, request);
        if (outcome != TransferOutcome.OK) {
            throw new TradeExecutionAbortedException();
        }
    }

    /**
     * Ghi cho MỖI bên: đã đưa gì, đã nhận gì (sửa 2026-10-03). Phiên + lời chào bị xoá ngay sau đây
     * ({@code deleteSession}, CASCADE), nên dòng audit này là dấu vết DUY NHẤT còn lại — bản cũ chỉ
     * có {@code sessionId}/{@code counterpartId}, không trả lời được "giao dịch đó chuyển gì".
     * {@code itemId} là dòng NGUỒN lúc thi hành (gộp chồng/tách chồng có thể cấp id khác cho bên nhận).
     */
    private void logAudit(Transaction t, TradeSessionSnapshot snapshot, Map<Long, OwnedItemSnapshot> offeredRows) {
        long requesterId = snapshot.requesterCharacterId();
        long targetId = snapshot.targetCharacterId();
        String requesterItems = itemsJson(snapshot.itemsOffered(requesterId), offeredRows);
        String targetItems = itemsJson(snapshot.itemsOffered(targetId), offeredRows);
        ports.auditPort()
                .log(
                        t,
                        requesterId,
                        "TRADE_EXECUTED",
                        auditDetail(
                                snapshot.id(),
                                targetId,
                                new AuditSide(snapshot.requesterGold(), requesterItems),
                                new AuditSide(snapshot.targetGold(), targetItems)));
        ports.auditPort()
                .log(
                        t,
                        targetId,
                        "TRADE_EXECUTED",
                        auditDetail(
                                snapshot.id(),
                                requesterId,
                                new AuditSide(snapshot.targetGold(), targetItems),
                                new AuditSide(snapshot.requesterGold(), requesterItems)));
    }

    /** Một chiều của giao dịch — vàng + mảng JSON các dòng vật phẩm. */
    private record AuditSide(long gold, String itemsJson) {}

    private static String auditDetail(long sessionId, long counterpartId, AuditSide given, AuditSide received) {
        return ("{\"sessionId\":%d,\"counterpartId\":%d,\"goldGiven\":%d,\"goldReceived\":%d,"
                        + "\"itemsGiven\":%s,\"itemsReceived\":%s}")
                .formatted(
                        sessionId,
                        counterpartId,
                        given.gold(),
                        received.gold(),
                        given.itemsJson(),
                        received.itemsJson());
    }

    // Chỉ số nguyên do server tự đọc từ DB — không có chuỗi người dùng, không cần thoát ký tự.
    // itemTemplateId + enhanceLevel (2026-10-14, README dòng 25(iii)) đọc từ dòng nguồn TRƯỚC khi
    // chuyển; giao dịch chỉ tới được đây khi mọi món đã chuyển được, nên dòng nào cũng có mặt.
    private static String itemsJson(List<TradeOfferItem> items, Map<Long, OwnedItemSnapshot> offeredRows) {
        StringJoiner joiner = new StringJoiner(",", "[", "]");
        for (TradeOfferItem item : items) {
            OwnedItemSnapshot row = offeredRows.get(item.itemId());
            joiner.add("{\"itemId\":%d,\"quantity\":%d,\"itemTemplateId\":%d,\"enhanceLevel\":%d}"
                    .formatted(
                            item.itemId(),
                            item.quantity(),
                            row == null ? 0L : row.itemTemplateId(),
                            row == null ? 0 : row.enhanceLevel()));
        }
        return joiner.toString();
    }

    public enum Result {
        OK,
        EXECUTED,
        NOT_FOUND,
        OFFER_INVALID,
        DUPLICATE_REQUEST
    }
}
