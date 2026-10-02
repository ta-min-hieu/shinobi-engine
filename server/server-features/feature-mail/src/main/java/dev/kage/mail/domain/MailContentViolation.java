package dev.kage.mail.domain;

/**
 * Lý do một {@link MailDraft} không hợp lệ về mặt NỘI DUNG — thứ kiểm được mà không cần đọc DB
 * (khác "không đủ vàng"/"không có món đó", phải hỏi DB nên nằm ở tầng application).
 *
 * <p>Tồn tại vì {@link NewMail}/{@link MailAttachment} enforce đúng các ràng buộc này bằng
 * {@link IllegalArgumentException} — hợp lý cho lời gọi nội bộ, nhưng dữ liệu từ client thì KHÔNG
 * được phép biến thành exception: nó bay lên tới {@code authExecutor} và người chơi không nhận
 * được gói trả lời nào (im lặng, không có lỗi để lần ra). Nên với dữ liệu client, kiểm TRƯỚC bằng
 * {@link MailDraft#validate()} rồi mới dựng {@link NewMail} — hai lớp cùng luật, lớp trong là chốt
 * phòng thủ cuối.
 */
public enum MailContentViolation {

    /** {@code goldAmount < 0} — trừ một số âm chính là CỘNG vàng cho người gửi. */
    GOLD_AMOUNT_NEGATIVE,

    /** {@code quantity <= 0} hoặc {@code itemTemplateId <= 0} ở một dòng đính kèm. */
    ATTACHMENT_INVALID,

    /** Vượt {@link MailDraft#MAX_SUBJECT_LENGTH}. */
    SUBJECT_TOO_LONG,

    /** Vượt {@link MailDraft#MAX_BODY_LENGTH}. */
    BODY_TOO_LONG,

    /**
     * Tiêu đề hoặc nội dung chứa {@code U+0000}. PostgreSQL không lưu được ký tự này trong cột
     * {@code text}/{@code varchar} — trước 2026-10-02 nó đi tới câu INSERT, nổ ở đó và người chơi
     * nhận 500 {@code error.server_internal}. Client thật không gõ ra được ký tự này.
     */
    TEXT_CONTAINS_NUL
}
