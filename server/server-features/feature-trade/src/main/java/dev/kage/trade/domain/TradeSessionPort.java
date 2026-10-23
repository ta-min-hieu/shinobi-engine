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

    /**
     * Tạo yêu cầu trade mới, trạng thái PENDING_ACCEPT — CHỈ KHI cả hai bên chưa có phiên nào đang
     * hoạt động, kiểm trong CÙNG giao dịch với lệnh tạo.
     *
     * <p>Vì sao không đủ để người gọi tự hỏi {@link #findActiveSessionOfCharacter} trước (đo thật
     * 2026-10-02): A mời T đúng lúc B mời T thì cả hai lần hỏi đều thấy T rảnh và cả hai phiên đều
     * được tạo — T kẹt trong HAI phiên, mà {@code GetTradeStatusUseCase} chỉ thấy được một.
     *
     * @throws TradeParticipantBusyException nếu một trong hai bên đã có phiên — không tạo gì cả
     */
    TradeSession createRequest(long requesterCharacterId, long targetCharacterId);

    /** Chấp nhận — điều kiện: {@code characterId} là target VÀ đang PENDING_ACCEPT. */
    boolean acceptRequest(long sessionId, long characterId);

    /** Huỷ/từ chối — điều kiện: {@code characterId} là 1 trong 2 bên. Xoá dòng (CASCADE offer items). */
    boolean deleteSessionIfParticipant(long sessionId, long characterId);

    /**
     * Xoá MỌI phiên còn lại (CASCADE offer items) — chỉ gọi lúc khởi động, khi chưa ai online: phiên sót lại
     * từ lần server chết đột ngột (dòng 40(iv)). Không có escrow nên xoá không mất gì.
     *
     * @return số phiên đã xoá
     */
    int deleteAllSessions();

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
