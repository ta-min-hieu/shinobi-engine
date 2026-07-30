package dev.kage.trade.application;

import dev.kage.trade.domain.TradeSession;
import dev.kage.trade.domain.TradeSessionPort;

/** Gửi yêu cầu trade — chỉ được khi CẢ 2 bên chưa có phiên trade nào đang hoạt động. */
public final class RequestTradeUseCase {

    private final TradeSessionPort port;

    public RequestTradeUseCase(TradeSessionPort port) {
        this.port = port;
    }

    public Result execute(long requesterCharacterId, long targetCharacterId) {
        if (requesterCharacterId == targetCharacterId) {
            return new Result(Outcome.CANNOT_TRADE_SELF, null);
        }
        if (port.findActiveSessionOfCharacter(requesterCharacterId).isPresent()) {
            return new Result(Outcome.ALREADY_IN_TRADE, null);
        }
        if (port.findActiveSessionOfCharacter(targetCharacterId).isPresent()) {
            return new Result(Outcome.TARGET_ALREADY_IN_TRADE, null);
        }
        return new Result(Outcome.OK, port.createRequest(requesterCharacterId, targetCharacterId));
    }

    public enum Outcome {
        OK,
        CANNOT_TRADE_SELF,
        ALREADY_IN_TRADE,
        TARGET_ALREADY_IN_TRADE
    }

    public record Result(Outcome outcome, TradeSession session) {}
}
