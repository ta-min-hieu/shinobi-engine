package dev.kage.trade.domain;

/**
 * Ném khi adapter định tạo phiên mới mà một trong hai bên ĐÃ có phiên đang hoạt động — phép kiểm
 * nằm CÙNG giao dịch với lệnh tạo, sau khoá theo từng nhân vật (xem
 * {@link TradeSessionPort#createRequest}). Đây là trọng tài thật của luật "mỗi người một phiên";
 * phép kiểm sớm ở {@code RequestTradeUseCase} chỉ để trả lời nhanh.
 */
public final class TradeParticipantBusyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TradeParticipantBusyException(long requesterCharacterId, long targetCharacterId) {
        super("Một trong hai bên đã có phiên trade đang hoạt động: " + requesterCharacterId + ", " + targetCharacterId);
    }
}
