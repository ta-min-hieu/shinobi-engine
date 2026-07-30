package dev.kage.trade.application;

import dev.kage.trade.domain.TradeSessionPort;

/**
 * Huỷ trade — dùng chung cho CẢ từ chối (còn PENDING_ACCEPT) lẫn huỷ giữa chừng (đang OPEN), 1
 * trong 2 bên gọi lúc nào cũng được trước khi thi hành xong. Không escrow gì để hoàn trả (xem
 * feature-trade/CLAUDE.md) — xoá dòng là xong.
 */
public final class CancelTradeUseCase {

    private final TradeSessionPort port;

    public CancelTradeUseCase(TradeSessionPort port) {
        this.port = port;
    }

    public Result execute(long sessionId, long characterId) {
        return port.deleteSessionIfParticipant(sessionId, characterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
