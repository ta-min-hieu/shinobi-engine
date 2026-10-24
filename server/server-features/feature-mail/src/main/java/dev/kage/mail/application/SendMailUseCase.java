package dev.kage.mail.application;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import dev.kage.mail.domain.IdempotencyKeyReusedException;
import dev.kage.mail.domain.Mail;
import dev.kage.mail.domain.MailAttachment;
import dev.kage.mail.domain.MailAttachmentDraft;
import dev.kage.mail.domain.MailContentViolation;
import dev.kage.mail.domain.MailDraft;
import dev.kage.mail.domain.NewMail;
import dev.kage.mail.domain.Postage;
import dev.kage.mail.domain.Transaction;
import dev.kage.mail.domain.TransactionRunner;

/**
 * Gửi thư — KÝ QUỸ vàng và vật phẩm đính kèm khỏi người gửi, TRONG CÙNG transaction với
 * {@code INSERT INTO mail}.
 *
 * <p><b>Lỗ hổng được vá ở đây (2026-08-28).</b> Trước đó use-case này chỉ có
 * {@code MailRepositoryPort}: {@code gold_amount} và danh sách đính kèm đi thẳng từ packet của
 * client vào bảng {@code mail}/{@code mail_item} mà không ai bị trừ gì — trong khi
 * {@link ClaimMailUseCase} thì cộng vàng thật và cấp item thật. Ghép hai đầu lại: tự gửi thư cho
 * chính mình kèm số vàng bất kỳ rồi nhận = đúc vàng từ không khí; gửi cùng một món cho 10 người =
 * nhân bản nó 10 lần. Đây là vi phạm thẳng Server Authority (/CLAUDE.md §5) — số liệu trong packet
 * của client được ghi thành hiệu ứng thật mà server không kiểm lại gì.
 *
 * <p><b>Thứ tự trong transaction</b> — trừ TRƯỚC, ghi thư SAU. Trừ trước thì nhánh "không đủ" chỉ
 * cần abort chứ không phải dọn một lá thư đã lỡ tạo; và vì tất cả nằm trong một transaction, thứ
 * tự không đổi được tính nguyên tử, chỉ đổi độ dễ đọc.
 *
 * <p><b>Thư hệ thống ({@code senderId == null}) KHÔNG bị ký quỹ</b> — không có ai để trừ, đó chính
 * là ý nghĩa của thư đền bù GM/quest reward tồn đọng (xem {@link Mail}). Đường này KHÔNG với tới
 * được từ client: {@code GatewayChannelHandler} luôn truyền {@code characterId} lấy từ SESSION,
 * không bao giờ {@code null} (server/CLAUDE.md — characterId lấy từ session, không từ packet).
 *
 * <p><b>Phí thư (ADR-076, 2026-10-24).</b> Thư người chơi có đính kèm mất thêm {@link Postage} — trừ
 * CÙNG câu với vàng đính kèm (một {@code UPDATE ... WHERE gold >= vàng + phí}), nên "đủ vàng đính kèm
 * nhưng thiếu phí" cũng là {@link SendOutcome#INSUFFICIENT_GOLD} và không đổi gì. Phí ghi vào audit.
 */
public final class SendMailUseCase {

    /**
     * Trần số thư một hộp giữ được trước khi người chơi khác không gửi thêm được (dòng 41(iv), chủ dự án
     * duyệt 2026-10-17). Trước đó không có trần: spam thư là đường quấy rối không tốn gì ngoài trần tần
     * suất. Đếm MỌI thư còn trong hộp (đã lĩnh mà chưa xoá cũng tính). Thư HỆ THỐNG ({@code senderId}
     * rỗng) không bị chặn — nó chở phần thưởng, chặn là mất thưởng. Trần mềm: hai người gửi cùng lúc vào
     * hộp 99 thư có thể cùng lọt — không khoá hộp người nhận chỉ để giữ đúng con số 100.
     */
    public static final int MAILBOX_CAPACITY = 100;

    private final MailPorts ports;
    private final TransactionRunner tx;

    public SendMailUseCase(MailPorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    /**
     * Kiểm nội dung, ký quỹ tài sản người gửi, rồi tạo thư — tất cả trong một transaction.
     *
     * @return {@link SendResult#outcome()} khác {@link SendOutcome#OK} thì KHÔNG có gì bị đổi —
     *     không thư, không vàng, không vật phẩm — và {@link SendResult#mail()} rỗng
     */
    public SendResult execute(MailDraft draft) {
        return execute(draft, "");
    }

    /**
     * Như {@link #execute(MailDraft)}, kèm khoá chống gửi trùng của người chơi (ADR-058).
     *
     * @param idempotencyKey khoá ADR-025/ADR-058; rỗng = không chống trùng. Ghi TRONG transaction, ngay
     *     sau khoá túi đồ người gửi và TRƯỚC khi trừ vàng/đồ — trùng thì rollback trọn.
     */
    public SendResult execute(MailDraft draft, String idempotencyKey) {
        Optional<MailContentViolation> violation = draft.validate();
        if (violation.isPresent()) {
            return SendResult.rejected(toOutcome(violation.get()));
        }
        if (draft.senderId() != null
                && ports.repositoryPort().listForRecipient(draft.recipientId()).size() >= MAILBOX_CAPACITY) {
            return SendResult.rejected(SendOutcome.MAILBOX_FULL);
        }
        NewMail newMail = draft.toNewMail();
        AtomicReference<Mail> resultHolder = new AtomicReference<Mail>();
        try {
            tx.inTransaction((Transaction t) -> {
                escrowFromSender(t, draft, idempotencyKey);
                Mail sent = ports.repositoryPort().send(t, newMail);
                auditIfEscrowed(t, draft, sent);
                resultHolder.set(sent);
            });
        } catch (SendMailAbortedException e) {
            return SendResult.rejected(e.outcome());
        } catch (IdempotencyKeyReusedException e) {
            return SendResult.rejected(SendOutcome.DUPLICATE_REQUEST);
        }
        return new SendResult(SendOutcome.OK, Optional.of(resultHolder.get()));
    }

    /**
     * Trừ vàng + vật phẩm của người gửi. Ném {@link SendMailAbortedException} thay vì trả về cờ:
     * đang ở giữa transaction, "return sớm" sẽ COMMIT phần đã trừ (cùng cái bẫy mà
     * {@link ClaimMailUseCase} đã chú thích).
     */
    private void escrowFromSender(Transaction t, MailDraft draft, String idempotencyKey) {
        Long senderId = draft.senderId();
        if (senderId == null) {
            recordKeyIfAny(t, idempotencyKey);
            return;
        }
        // Khoá túi đồ người gửi ĐẦU TIÊN, trước vàng — thứ tự khoá chung (DATABASE.md §5.1).
        // Đây là lời gọi DB đầu tiên của transaction gửi thư.
        ports.inventoryGatewayPort().lockInventory(t, senderId);
        recordKeyIfAny(t, idempotencyKey);
        long totalGold = draft.goldAmount()
                + Postage.feeFor(draft.goldAmount(), draft.attachments().size());
        // totalGold < 0 chỉ khi cộng tràn long — số vàng đính kèm vô lý, không ai có đủ.
        if (totalGold < 0 || (totalGold > 0 && !ports.goldPort().deductGold(t, senderId, totalGold))) {
            throw new SendMailAbortedException(SendOutcome.INSUFFICIENT_GOLD);
        }
        for (MailAttachmentDraft attachment : draft.attachments()) {
            boolean consumed = ports.inventoryGatewayPort().consumeItem(t, senderId, attachment.toAttachment());
            if (!consumed) {
                throw new SendMailAbortedException(SendOutcome.ATTACHMENT_NOT_OWNED);
            }
        }
    }

    /**
     * Chỉ ghi audit khi thật sự có tài sản rời khỏi túi ai đó — DATABASE.md §5 yêu cầu audit cho
     * "mail-item"/gold, còn thư chào hỏi rỗng thì ghi vào đây chỉ làm loãng bảng {@code audit_log}.
     */
    private void recordKeyIfAny(Transaction t, String idempotencyKey) {
        if (!idempotencyKey.isEmpty()) {
            ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
        }
    }

    private void auditIfEscrowed(Transaction t, MailDraft draft, Mail sent) {
        if (draft.senderId() == null || !sent.hasReward()) {
            return;
        }
        String detail = ("{\"mailId\":%d,\"recipientId\":%d,\"goldAmount\":%d,\"postage\":%d,"
                        + "\"itemCount\":%d,\"items\":%s}")
                .formatted(
                        sent.id(),
                        sent.recipientId(),
                        sent.goldAmount(),
                        Postage.feeFor(sent.goldAmount(), sent.attachments().size()),
                        sent.attachments().size(),
                        MailAttachment.auditJson(sent.attachments()));
        ports.auditPort().log(t, draft.senderId(), "MAIL_SEND", detail);
    }

    private static SendOutcome toOutcome(MailContentViolation violation) {
        return switch (violation) {
            case GOLD_AMOUNT_NEGATIVE -> SendOutcome.INVALID_GOLD_AMOUNT;
            case ATTACHMENT_INVALID -> SendOutcome.INVALID_ATTACHMENT;
            case SUBJECT_TOO_LONG -> SendOutcome.SUBJECT_TOO_LONG;
            case BODY_TOO_LONG -> SendOutcome.BODY_TOO_LONG;
            case TEXT_CONTAINS_NUL -> SendOutcome.INVALID_TEXT;
        };
    }

    /** Thư đã tạo chỉ tồn tại khi {@code outcome == OK} — không dùng {@code null} làm sentinel (/CLAUDE.md §11). */
    public record SendResult(SendOutcome outcome, Optional<Mail> mail) {

        static SendResult rejected(SendOutcome outcome) {
            return new SendResult(outcome, Optional.empty());
        }
    }

    public enum SendOutcome {
        OK,
        /** Không đủ vàng cho số đính kèm cộng phí thư ({@link Postage}). */
        INSUFFICIENT_GOLD,
        /** Không có/không đủ món đó trong túi — kể cả khi {@code itemTemplateId} không tồn tại. */
        ATTACHMENT_NOT_OWNED,
        INVALID_GOLD_AMOUNT,
        INVALID_ATTACHMENT,
        SUBJECT_TOO_LONG,
        BODY_TOO_LONG,
        /** Tiêu đề/nội dung chứa ký tự không lưu được ({@code U+0000}). */
        INVALID_TEXT,
        /** Hộp thư người nhận đã có {@link #MAILBOX_CAPACITY} thư (2026-10-17). */
        MAILBOX_FULL,
        /** {@code idempotency_key} đã dùng (ADR-058) — lá thư đó đã được gửi trước đó. */
        DUPLICATE_REQUEST
    }
}
