package dev.kage.trade.application;

import dev.kage.trade.domain.TradeSessionPort;

/** Target chấp nhận yêu cầu trade — chuyển PENDING_ACCEPT sang OPEN. */
public final class AcceptTradeRequestUseCase {

    private final TradeSessionPort port;

    public AcceptTradeRequestUseCase(TradeSessionPort port) {
        this.port = port;
    }

    public Result execute(long sessionId, long characterId) {
        return port.acceptRequest(sessionId, characterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
