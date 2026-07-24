package dev.kage.mail.api;

import dev.kage.mail.application.ClaimMailUseCase;
import dev.kage.mail.application.DeleteMailUseCase;
import dev.kage.mail.application.ListMailUseCase;
import dev.kage.mail.application.SendMailUseCase;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.NewMail;
import java.util.List;

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

    public MailFacade(
            SendMailUseCase sendMailUseCase, ListMailUseCase listMailUseCase, ClaimMailUseCase claimMailUseCase,
            DeleteMailUseCase deleteMailUseCase) {
        this.sendMailUseCase = sendMailUseCase;
        this.listMailUseCase = listMailUseCase;
        this.claimMailUseCase = claimMailUseCase;
        this.deleteMailUseCase = deleteMailUseCase;
    }

    /** Gửi thư — LUÔN thành công (recipientId đã được gateway xác nhận tồn tại trước khi gọi). */
    public long sendMail(SendMailRequest request) {
        var domainAttachments = request.attachments().stream()
                .map(a -> new MailAttachment(a.itemTemplateId(), a.quantity()))
                .toList();
        var newMail = new NewMail(
                request.senderId(), request.recipientId(), request.subject(), request.body(), request.goldAmount(),
                domainAttachments);
        return sendMailUseCase.execute(newMail).id();
    }

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

    public DeleteResult deleteMail(long mailId, long characterId) {
        return switch (deleteMailUseCase.execute(mailId, characterId)) {
            case OK -> DeleteResult.OK;
            case NOT_FOUND -> DeleteResult.NOT_FOUND;
            case MUST_CLAIM_FIRST -> DeleteResult.MUST_CLAIM_FIRST;
        };
    }

    public enum ClaimResult { OK, NOT_FOUND, ALREADY_CLAIMED, INVENTORY_FULL, DUPLICATE_REQUEST }

    public enum DeleteResult { OK, NOT_FOUND, MUST_CLAIM_FIRST }

    public record AttachmentRequest(long itemTemplateId, int quantity) {
    }

    /** Gộp tham số gửi thư — tránh {@link #sendMail} vượt 4 tham số (Checkstyle ParameterNumber). */
    public record SendMailRequest(
            Long senderId, long recipientId, String subject, String body, long goldAmount,
            List<AttachmentRequest> attachments) {
    }

    public record AttachmentView(long itemTemplateId, int quantity) {
    }

    public record MailView(
            long mailId, Long senderId, String subject, String body, long goldAmount,
            List<AttachmentView> attachments, boolean claimed) {
    }
}
