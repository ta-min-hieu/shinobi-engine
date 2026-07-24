package dev.kage.trade.application;

import dev.kage.trade.domain.OfferedItemInput;
import dev.kage.trade.domain.TradeSessionPort;
import java.util.List;

/**
 * Đặt lại lời chào (gold + item) — KHÔNG xác thực sở hữu ở đây, chỉ lưu Ý ĐỊNH (xem
 * feature-trade/CLAUDE.md, xác thực thật ở {@link ConfirmTradeUseCase} lúc thi hành).
 */
public final class SetTradeOfferUseCase {

    private final TradeSessionPort port;

    public SetTradeOfferUseCase(TradeSessionPort port) {
        this.port = port;
    }

    public Result execute(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items) {
        if (goldAmount < 0) {
            throw new IllegalArgumentException("goldAmount không được âm: " + goldAmount);
        }
        return port.replaceOffer(sessionId, characterId, goldAmount, items) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result { OK, NOT_FOUND }
}
