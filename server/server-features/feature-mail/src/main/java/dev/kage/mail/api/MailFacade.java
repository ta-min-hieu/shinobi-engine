package dev.kage.mail.api;

import java.util.List;

import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailDraft;

/**
 * API công khai của {@code feature-mail} — module khác (server-gateway) CHỈ gọi qua đây, không
 * bao giờ đọc bảng {@code mail}/{@code mail_item} trực tiếp (Điều 24). {@code senderId == null}
 * nghĩa là thư hệ thống — gateway tự quyết định khi nào gửi thư nhân danh 1 nhân vật thật.
 */
public final class MailFacade {

    private final SendMailUseCase sendMailUseCase;
    private final ListMailUseCase listMailUseCase;
    private final ClaimMailUseCase claimMailUseCase;
    private final DeleteMailUseCase deleteMailUseCase;

    /** Dựng ở composition root. */
    public MailFacade(
            SendMailUseCase sendMailUseCase,
            ListMailUseCase listMailUseCase,
            ClaimMailUseCase claimMailUseCase,
            DeleteMailUseCase deleteMailUseCase) {
        this.sendMailUseCase = sendMailUseCase;
        this.listMailUseCase = listMailUseCase;
        this.claimMailUseCase = claimMailUseCase;
        this.deleteMailUseCase = deleteMailUseCase;
    }

    /**
     * Gửi thư — KHÔNG còn "luôn thành công" từ 2026-08-28: vàng/vật phẩm đính kèm bị trừ khỏi
     * người gửi trong cùng transaction với việc tạo thư, nên có nhánh trượt (xem {@link SendResult}).
     * {@code recipientId} vẫn do gateway xác nhận tồn tại trước khi gọi.
     */
    public SendOutcome sendMail(SendMailRequest request) {
        List<MailAttachmentDraft> draftAttachments = request.attachments().stream()
                .map(a -> new MailAttachmentDraft(a.itemTemplateId(), a.quantity()))
                .toList();
        MailDraft draft = new MailDraft(
                request.senderId(),
                request.recipientId(),
                request.subject(),
                request.body(),
                request.goldAmount(),
                draftAttachments);
        SendMailUseCase.SendResult result = sendMailUseCase.execute(draft);
        return new SendOutcome(
                toSendResult(result.outcome()), result.mail().map(Mail::id).orElse(0L));
    }

    /**
     * Hai enum cùng tên giá trị — đổi theo TÊN thay cho switch 10 nhánh (trần CyclomaticComplexity của
     * PMD). Lệch tên là IllegalArgumentException ngay ở test đầu tiên gọi tới.
     */
    private static SendResult toSendResult(SendMailUseCase.SendOutcome outcome) {
        return SendResult.valueOf(outcome.name());
    }

    /** Mọi thư trong hộp của {@code characterId}, kể cả thư đã lĩnh đồ đính kèm. */
    public List<MailView> listMail(long characterId) {
        return listMailUseCase.execute(characterId).stream()
                .map(mail -> new MailView(
                        mail.id(),
                        mail.senderId(),
                        mail.subject(),
                        mail.body(),
                        mail.goldAmount(),
                        mail.attachments().stream()
                                .map(a -> new AttachmentView(a.itemTemplateId(), a.quantity()))
                                .toList(),
                        mail.isClaimed()))
                .toList();
    }

    /** {@code idempotencyKey} (ADR-025) — client sinh UUID, giữ nguyên khi tự retry. */
    public ClaimResult claimMail(long mailId, long characterId, String idempotencyKey) {
        return switch (claimMailUseCase.execute(mailId, characterId, idempotencyKey)) {
            case OK -> ClaimResult.OK;
            case NOT_FOUND -> ClaimResult.NOT_FOUND;
            case ALREADY_CLAIMED -> ClaimResult.ALREADY_CLAIMED;
            case INVENTORY_FULL -> ClaimResult.INVENTORY_FULL;
            case DUPLICATE_REQUEST -> ClaimResult.DUPLICATE_REQUEST;
        };
    }

    /** Xoá thư — thư còn đồ/vàng chưa lĩnh thì phải lĩnh trước. */
    public DeleteResult deleteMail(long mailId, long characterId) {
        return switch (deleteMailUseCase.execute(mailId, characterId)) {
            case OK -> DeleteResult.OK;
            case NOT_FOUND -> DeleteResult.NOT_FOUND;
            case MUST_CLAIM_FIRST -> DeleteResult.MUST_CLAIM_FIRST;
        };
    }

    /** {@code mailId == 0} khi {@code result != OK} — không có thư nào được tạo. */
    public record SendOutcome(SendResult result, long mailId) {}

    /** Kết quả gửi thư. */
    public enum SendResult {
        OK,
        INSUFFICIENT_GOLD,
        ATTACHMENT_NOT_OWNED,
        INVALID_GOLD_AMOUNT,
        INVALID_ATTACHMENT,
        SUBJECT_TOO_LONG,
        BODY_TOO_LONG,
        /**
         * Tiêu đề/nội dung chứa {@code U+0000}. KHÔNG có giá trị {@code SendMailOutcome} tương ứng
         * (thêm là đổi protocol) — gateway trả {@code ErrorResponse} {@code error.invalid_payload}.
         */
        INVALID_TEXT,
        /** Hộp thư người nhận đã đầy — gateway trả {@code ErrorResponse} {@code error.mailbox_full}. */
        MAILBOX_FULL
    }

    /** Kết quả lĩnh đồ/vàng đính kèm. */
    public enum ClaimResult {
        OK,
        NOT_FOUND,
        ALREADY_CLAIMED,
        INVENTORY_FULL,
        DUPLICATE_REQUEST
    }

    /** Kết quả xoá thư. */
    public enum DeleteResult {
        OK,
        NOT_FOUND,
        MUST_CLAIM_FIRST
    }

    /** Một món đính kèm người gửi muốn gửi đi. */
    public record AttachmentRequest(long itemTemplateId, int quantity) {}

    /** Gộp tham số gửi thư — tránh {@link #sendMail} vượt 4 tham số (Checkstyle ParameterNumber). */
    public record SendMailRequest(
            Long senderId,
            long recipientId,
            String subject,
            String body,
            long goldAmount,
            List<AttachmentRequest> attachments) {
        /** Chép phòng thủ danh sách đính kèm. */
        public SendMailRequest {
            attachments = List.copyOf(attachments);
        }
    }

    /** Một món đính kèm trong thư đã nhận. */
    public record AttachmentView(long itemTemplateId, int quantity) {}

    /** Một thư trong hộp — {@code senderId} {@code null} là thư hệ thống. */
    public record MailView(
            long mailId,
            Long senderId,
            String subject,
            String body,
            long goldAmount,
            List<AttachmentView> attachments,
            boolean claimed) {
        /** Chép phòng thủ danh sách đính kèm. */
        public MailView {
            attachments = List.copyOf(attachments);
        }
    }
}
