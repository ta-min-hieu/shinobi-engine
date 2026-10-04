package dev.kage.mail.domain;

import java.util.List;
import java.util.Optional;

/** Port — DB-agnostic (Điều 23). Implementation thật ({@code PostgresMailAdapter}) nằm ở server-database. */
public interface MailRepositoryPort {

    /** Tạo thư mới + toàn bộ dòng đính kèm trong CÙNG transaction (mail + N item đính kèm là 1 đơn vị). */
    Mail send(Transaction tx, NewMail newMail);

    /** Đọc thuần, không cần transaction — dùng cho danh sách/kiểm tra trước khi xoá. */
    Optional<Mail> findById(long mailId);

    /**
     * Đọc + KHOÁ dòng ({@code SELECT ... FOR UPDATE}) trong transaction đang mở — bắt buộc trước
     * khi nhận thưởng để chống 2 request nhận cùng lúc (cùng lý do
     * {@code PostgresShopInventoryGatewayAdapter} khoá dòng lúc bán).
     */
    Optional<Mail> findByIdForUpdate(Transaction tx, long mailId);

    /** Toàn bộ thư của {@code recipientId}, mới nhất trước. */
    List<Mail> listForRecipient(long recipientId);

    /**
     * Đánh dấu đã nhận — UPDATE có điều kiện {@code WHERE id=? AND recipient_id=? AND claimed_at
     * IS NULL}, PHẢI gọi sau {@link #findByIdForUpdate} trong CÙNG transaction (khoá dòng trước).
     *
     * @return {@code true} nếu có dòng bị đổi
     */
    boolean markClaimed(Transaction tx, long mailId, long recipientId);

    /**
     * Xoá thư — application đã tự kiểm "đã nhận hoặc không có gì để nhận" trước khi gọi. Hiện thực
     * vẫn PHẢI tự ràng buộc {@code recipientId} (phòng thủ chiều sâu): người nhận sai thì không xoá
     * gì cả, kể cả vật phẩm đính kèm.
     *
     * @return {@code true} nếu có thư bị xoá
     */
    boolean delete(long mailId, long recipientId);
}
