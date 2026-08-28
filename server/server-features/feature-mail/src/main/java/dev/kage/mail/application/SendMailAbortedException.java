package dev.kage.mail.application;

/**
 * Rollback-signal nội bộ của {@link SendMailUseCase} — mang theo LÝ DO để trả về cho client.
 *
 * <p>Tách khỏi {@code MailTransactionAbortedException} (dùng lúc NHẬN thư, chỉ có đúng một lý do
 * là túi đầy) vì lúc GỬI có hai lý do khác nhau — không đủ vàng và không đủ món — mà cả hai đều
 * phải rollback: ký quỹ vàng có thể đã chạy xong trước khi phát hiện thiếu món, và "trừ vàng
 * nhưng thư không được tạo" còn tệ hơn lỗ hổng đang vá.
 */
final class SendMailAbortedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** Enum là {@code Serializable} nên field này không cần {@code transient} (SpotBugs SE_BAD_FIELD). */
    private final SendMailUseCase.SendOutcome outcome;

    SendMailAbortedException(SendMailUseCase.SendOutcome outcome) {
        this.outcome = outcome;
    }

    SendMailUseCase.SendOutcome outcome() {
        return outcome;
    }
}
