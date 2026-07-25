package dev.kage.trade.application;

import dev.kage.trade.domain.TradeInventoryPort;
import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionPort;
import dev.kage.trade.domain.TradeSessionSnapshot;
import java.util.List;
import java.util.Optional;

/**
 * Trạng thái phiên trade hiện tại của {@code characterId} — rỗng nếu không có phiên nào đang hoạt
 * động. Ghép thêm {@code itemTemplateId}/{@code enhanceLevel} cho từng item chào (đọc thuần, N+1
 * chấp nhận được ở quy mô 1 lời chào vài item, cùng tinh thần {@code ListFriendsResponse}) — API
 * facade chỉ cần map thẳng sang protocol view, không tự đọc Port.
 */
public final class GetTradeStatusUseCase {

    private final TradeSessionPort sessionPort;
    private final TradeInventoryPort inventoryPort;

    public GetTradeStatusUseCase(TradeSessionPort sessionPort, TradeInventoryPort inventoryPort) {
        this.sessionPort = sessionPort;
        this.inventoryPort = inventoryPort;
    }

    public Optional<Result> execute(long characterId) {
        Optional<TradeSession> membership = sessionPort.findActiveSessionOfCharacter(characterId);
        if (membership.isEmpty()) {
            return Optional.empty();
        }
        Optional<TradeSessionSnapshot> snapshotOpt = sessionPort.findSnapshot(membership.get().id());
        if (snapshotOpt.isEmpty()) {
            return Optional.empty();
        }
        TradeSessionSnapshot snapshot = snapshotOpt.get();
        List<ResolvedOfferItem> resolvedItems = snapshot.items().stream()
                .map(item -> inventoryPort.findItemById(item.itemId())
                        .map(owned -> new ResolvedOfferItem(
                                item.offeringCharacterId(), item.itemId(), owned.itemTemplateId(),
                                item.quantity(), owned.enhanceLevel()))
                        .orElse(new ResolvedOfferItem(item.offeringCharacterId(), item.itemId(), 0,
                                item.quantity(), 0)))
                .toList();
        return Optional.of(new Result(
                snapshot.id(), snapshot.requesterCharacterId(), snapshot.targetCharacterId(), snapshot.status(),
                snapshot.requesterGold(), snapshot.targetGold(), snapshot.requesterConfirmed(),
                snapshot.targetConfirmed(), resolvedItems));
    }

    /** {@code itemTemplateId=0}/{@code enhanceLevel=0} nếu item đã bị bán/gửi/xoá giữa chừng (tham chiếu mềm). */
    public record ResolvedOfferItem(
            long offeringCharacterId, long itemId, long itemTemplateId, int quantity, int enhanceLevel) {
    }

    public record Result(
            long sessionId,
            long requesterCharacterId,
            long targetCharacterId,
            dev.kage.trade.domain.TradeStatus status,
            long requesterGold,
            long targetGold,
            boolean requesterConfirmed,
            boolean targetConfirmed,
            List<ResolvedOfferItem> items) {
    }
}
