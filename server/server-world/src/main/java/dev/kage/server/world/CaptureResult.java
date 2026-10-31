package dev.kage.server.world;

/**
 * Kết quả một lần thử thu phục yêu thú (ADR-124) — world kiểm và (nếu trúng) gỡ quái trong CÙNG một lượt tick.
 *
 * @param monsterCode mã loài của con quái được nhắm; rỗng khi {@link Outcome#NOT_FOUND}
 */
public record CaptureResult(Outcome outcome, String monsterCode) {

    /** Kết quả. */
    public enum Outcome {
        /** Không có con quái này (đã chết, id lạ) hoặc người thử không ở world này. */
        NOT_FOUND,
        TOO_FAR,
        HP_TOO_HIGH,
        /** Loài này không thu phục được. */
        NOT_TAMEABLE,
        /** Tung trượt — quái vẫn ở đó. */
        ESCAPED,
        /** Tung trúng — quái đã biến mất khỏi world (không rơi đồ, không EXP), điểm spawn lên lịch hồi sinh. */
        CAPTURED
    }
}
