package dev.kage.mail.application;

import dev.kage.mail.domain.IdempotencyKeyReusedException;
import dev.kage.mail.domain.Transaction;
import dev.kage.mail.domain.TransactionRunner;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Nhận gold + item đính kèm của 1 lá thư — TRONG CÙNG 1 transaction: ghi {@code idempotencyKey}
 * (ADR-025) → khoá dòng ({@code findByIdForUpdate}) → kiểm chưa nhận → cộng gold → cấp item →
 * đánh dấu {@code claimed_at} → ghi audit. Đây CHÍNH XÁC là chỗ GAMEPLAY.md §11 cảnh báo "nhận
 * item nhưng không đánh dấu đã nhận -> nhận lại được" — mọi bước ở trên PHẢI cùng transaction,
 * không tách rời.
 */
public final class ClaimMailUseCase {

    private final MailPorts ports;
    private final TransactionRunner tx;

    public ClaimMailUseCase(MailPorts ports, TransactionRunner tx) {
        this.ports = ports;
        this.tx = tx;
    }

    public ClaimOutcome execute(long mailId, long recipientId, String idempotencyKey) {
        var resultHolder = new AtomicReference<ClaimOutcome>();
        try {
            tx.inTransaction((Transaction t) -> {
                ports.idempotencyPort().recordOrThrow(t, idempotencyKey);
                var mailOpt = ports.repositoryPort().findByIdForUpdate(t, mailId);
                if (mailOpt.isEmpty() || mailOpt.get().recipientId() != recipientId) {
                    resultHolder.set(ClaimOutcome.NOT_FOUND);
                    return;
                }
                var mail = mailOpt.get();
                if (mail.isClaimed()) {
                    resultHolder.set(ClaimOutcome.ALREADY_CLAIMED);
                    return;
                }
                if (mail.goldAmount() > 0) {
                    ports.goldPort().creditGold(t, recipientId, mail.goldAmount());
                }
                for (var attachment : mail.attachments()) {
                    boolean granted = ports.inventoryGatewayPort()
                            .grantItem(t, recipientId, attachment.itemTemplateId(), attachment.quantity());
                    if (!granted) {
                        // Túi đầy giữa chừng — rollback CẢ gold đã cộng ở trên, KHÔNG "return" sớm
                        // (return thường sẽ COMMIT phần đã làm, xem javadoc lớp).
                        throw new MailTransactionAbortedException();
                    }
                }
                if (!ports.repositoryPort().markClaimed(t, mailId, recipientId)) {
                    // Race: request khác đã claim xong giữa lúc findByIdForUpdate và đây là không
                    // thể xảy ra (FOR UPDATE khoá dòng), nhưng giữ nhánh phòng thủ thay vì giả định.
                    throw new MailTransactionAbortedException();
                }
                var detail = "{\"mailId\":%d,\"goldAmount\":%d,\"itemCount\":%d}"
                        .formatted(mailId, mail.goldAmount(), mail.attachments().size());
                ports.auditPort().log(t, recipientId, "MAIL_CLAIM", detail);
                resultHolder.set(ClaimOutcome.OK);
            });
        } catch (IdempotencyKeyReusedException e) {
            return ClaimOutcome.DUPLICATE_REQUEST;
        } catch (MailTransactionAbortedException e) {
            return ClaimOutcome.INVENTORY_FULL;
        }
        return resultHolder.get();
    }

    public enum ClaimOutcome { OK, NOT_FOUND, ALREADY_CLAIMED, INVENTORY_FULL, DUPLICATE_REQUEST }
}
