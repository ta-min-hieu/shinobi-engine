package dev.kage.server.gateway;

import java.util.List;
import java.util.UUID;

import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ClaimAllMailCommand;
import dev.kage.protocol.ClaimAllMailResponse;

/**
 * Nhận hết thư (ADR-167) — lĩnh từng thư một qua ĐÚNG đường lĩnh thư thường ({@link MailFacade#claimMail}: mỗi thư một
 * transaction, khoá túi, audit), theo thứ tự hộp thư; túi đầy thì dừng (thư sau đó còn nguyên). Rồi xoá thư đã xong nếu
 * được yêu cầu. Không đổi schema, không thêm đường ghi mới.
 */
final class MailBulkCommands {

    private final MailFacade mail;

    MailBulkCommands(MailFacade mail) {
        this.mail = mail;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    ClaimAllMailResponse claimAll(long characterId, ClaimAllMailCommand command) {
        ClaimAllMailResponse.Builder response = ClaimAllMailResponse.newBuilder();
        List<MailFacade.MailView> inbox = mail.listMail(characterId);
        int claimed = 0;
        long gold = 0L;
        int items = 0;
        for (MailFacade.MailView view : inbox) {
            if (view.claimed() || !hasSomething(view)) {
                continue;
            }
            MailFacade.ClaimResult result =
                    mail.claimMail(view.mailId(), characterId, UUID.randomUUID().toString());
            if (result == MailFacade.ClaimResult.INVENTORY_FULL) {
                response.setBagFull(true);
                break;
            }
            if (result == MailFacade.ClaimResult.OK) {
                claimed++;
                gold += view.goldAmount();
                items += view.attachments().stream()
                        .mapToInt(MailFacade.AttachmentView::quantity)
                        .sum();
            }
        }
        response.setClaimed(claimed).setGold(gold).setItems(items);
        if (command.getDeleteClaimed()) {
            response.setDeleted(deleteFinished(characterId));
        }
        return response.build();
    }

    /** Xoá mọi thư không còn gì để lĩnh (đã lĩnh, hoặc thư chữ). Thư còn đồ chưa lĩnh: facade tự từ chối. */
    private int deleteFinished(long characterId) {
        int deleted = 0;
        for (MailFacade.MailView view : mail.listMail(characterId)) {
            if ((view.claimed() || !hasSomething(view))
                    && mail.deleteMail(view.mailId(), characterId) == MailFacade.DeleteResult.OK) {
                deleted++;
            }
        }
        return deleted;
    }

    private static boolean hasSomething(MailFacade.MailView view) {
        return view.goldAmount() > 0 || !view.attachments().isEmpty();
    }
}
