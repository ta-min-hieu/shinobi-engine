package dev.kage.trade.domain;

import java.util.List;
import java.util.Optional;

/**
 * Port quản lý vòng đời {@code trade_session}/{@code trade_offer_item} — DB-agnostic (Điều 23).
 * Implementation thật ({@code PostgresTradeSessionAdapter}) nằm ở server-database.
 */
public interface TradeSessionPort {

    /** Phiên đang PENDING_ACCEPT hoặc OPEN mà {@code characterId} tham gia (bất kỳ vai trò) — rỗng nếu không có. */
    Optional<TradeSession> findActiveSessionOfCharacter(long characterId);

    /** Tạo yêu cầu trade mới, trạng thái PENDING_ACCEPT. */
    TradeSession createRequest(long requesterCharacterId, long targetCharacterId);

    /** Chấp nhận — điều kiện: {@code characterId} là target VÀ đang PENDING_ACCEPT. */
    boolean acceptRequest(long sessionId, long characterId);

    /** Huỷ/từ chối — điều kiện: {@code characterId} là 1 trong 2 bên. Xoá dòng (CASCADE offer items). */
    boolean deleteSessionIfParticipant(long sessionId, long characterId);

    /**
     * Đặt lại TOÀN BỘ lời chào của {@code characterId} — xoá offer item cũ, thêm offer item mới,
     * cập nhật gold, RESET CẢ 2 cờ confirmed về false (sửa lời chào sau khi 1 bên đã confirm phải
     * confirm lại — chống lừa đảo đổi lời chào giữa chừng, GAMEPLAY.md §8). KHÔNG xác thực sở hữu
     * ở đây (xem feature-trade/CLAUDE.md) — điều kiện: phiên đang OPEN VÀ {@code characterId} là
     * 1 trong 2 bên.
     */
    boolean replaceOffer(long sessionId, long characterId, long goldAmount, List<OfferedItemInput> items);

    /** Đọc thuần (không khoá) — toàn bộ trạng thái phiên, dùng cho xem trạng thái. */
    Optional<TradeSessionSnapshot> findSnapshot(long sessionId);

    /** Khoá dòng FOR UPDATE — CHỈ dùng trong transaction thi hành ({@code ConfirmTradeUseCase}). */
    Optional<TradeSessionSnapshot> lockForExecution(Transaction tx, long sessionId);

    /** Đánh dấu {@code characterId} đã confirm — adapter tự xác định là requester hay target. */
    void setConfirmed(Transaction tx, long sessionId, long characterId);

    /** Xoá phiên SAU KHI thi hành xong — dùng trong transaction (khác {@link #deleteSessionIfParticipant}). */
    void deleteSession(Transaction tx, long sessionId);
}
