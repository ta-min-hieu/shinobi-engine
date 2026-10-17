package dev.kage.trade.application;

import java.util.List;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSessionPort;

/**
 * Đặt lại lời chào (gold + item) — KHÔNG xác thực sở hữu ở đây, chỉ lưu Ý ĐỊNH (xem
 * feature-trade/CLAUDE.md, xác thực thật ở {@link ConfirmTradeUseCase} lúc thi hành).
 */
public final class SetTradeOfferUseCase {

    private final TradeSessionPort port;
    private final TradeOfferChanges offerChanges;

    /** Sổ {@link TradeOfferChanges} riêng — chỉ dành cho test không quan tâm luật chặn xác nhận. */
    public SetTradeOfferUseCase(TradeSessionPort port) {
        this(port, new TradeOfferChanges());
    }

    /** {@code offerChanges} dùng chung với {@link ConfirmTradeUseCase} — xem {@link TradeOfferChanges}. */
    public SetTradeOfferUseCase(TradeSessionPort port, TradeOfferChanges offerChanges) {
        this.port = port;
        this.offerChanges = offerChanges;
    }

    public Result execute(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {
        if (goldAmount < 0) {
            throw new IllegalArgumentException("goldAmount không được âm: " + goldAmount);
        }
        if (!port.replaceOffer(sessionId, characterId, goldAmount, items)) {
            return Result.NOT_FOUND;
        }
        offerChanges.recordChange(sessionId, characterId);
        return Result.OK;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
