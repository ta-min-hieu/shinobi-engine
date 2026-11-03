package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import dev.kage.gm.api.CompensationFacade;
import dev.kage.gm.api.GmFacade;
import dev.kage.gm.domain.CompensationPort;
import dev.kage.gm.domain.GmCommand;
import dev.kage.gm.domain.GmOutcome;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;

/**
 * {@code /gm compensate} (ADR-165) — thư hệ thống mang phần quà tới MỌI nhân vật cấp ≥ ngưỡng. Mỗi thư một khoá chống
 * trùng TẤT ĐỊNH ({@code UUID} từ mã đợt + id nhân vật) nên chạy lại một đợt dang dở (server sập giữa chừng) không gửi
 * trùng; đợt đã xong thì từ chối. Chạy đồng bộ trên Virtual Thread của lệnh GM, trả lời khi xong.
 */
final class GmCompensationCommand {

    static final String SUBJECT = "Bồi thường / Compensation";
    static final String BODY = "Quà bồi thường từ ban quản trị. / A compensation gift from the team.";

    /** Nguồn dữ liệu. */
    record Deps(
            CompensationFacade compensations,
            MailFacade mail,
            InventoryFacade inventory,
            ConnectedCharacterRegistry registry) {}

    /** Đếm kết quả một lần chạy. */
    private record Tally(int sent, int duplicate, int failed) {}

    private final Deps deps;
    private final Clock clock;

    GmCompensationCommand(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    String run(GmCommand.Compensate command, long actorId, GmFacade.AuditTicket ticket) {
        Optional<Long> itemTemplateId = itemOf(command.itemCode());
        if (itemTemplateId.isEmpty()) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "No item '" + command.itemCode() + "'";
        }
        long item = itemTemplateId.get();
        CompensationPort.Campaign campaign = new CompensationPort.Campaign(
                command.code(), command.gold(), item, item > 0 ? command.quantity() : 0, command.minLevel());
        CompensationPort.Start start = deps.compensations().begin(campaign, actorId, clock.instant());
        if (start == CompensationPort.Start.DONE || start == CompensationPort.Start.CONFLICT) {
            ticket.finish(GmOutcome.REJECTED, "{\"start\":\"" + start + "\"}");
            return start == CompensationPort.Start.DONE
                    ? "Compensation " + command.code() + " already completed"
                    : "Code " + command.code() + " already used for a different reward";
        }
        List<Long> recipients = deps.compensations().recipients(command.minLevel());
        Tally tally = send(campaign, recipients);
        deps.compensations().finish(command.code(), tally.sent(), clock.instant());
        ticket.finish(
                tally.failed() == 0 ? GmOutcome.OK : GmOutcome.FAILED,
                "{\"code\":\"%s\",\"start\":\"%s\",\"recipients\":%d,\"sent\":%d,\"duplicate\":%d,\"failed\":%d}"
                        .formatted(
                                command.code(),
                                start,
                                recipients.size(),
                                tally.sent(),
                                tally.duplicate(),
                                tally.failed()));
        return "Compensation %s (%s): sent %d, already had %d, failed %d of %d characters (level >= %d)"
                .formatted(
                        command.code(),
                        start,
                        tally.sent(),
                        tally.duplicate(),
                        tally.failed(),
                        recipients.size(),
                        command.minLevel());
    }

    /** Rỗng = mã đồ lạ; {@code 0} = không kèm đồ. */
    private Optional<Long> itemOf(String itemCode) {
        if (itemCode.isEmpty()) {
            return Optional.of(0L);
        }
        return deps.inventory().itemCatalog().entries().stream()
                .filter(entry -> entry.code().equals(itemCode))
                .findFirst()
                .map(ItemCatalogEntry::id);
    }

    private Tally send(CompensationPort.Campaign campaign, List<Long> recipients) {
        List<MailFacade.AttachmentRequest> items = campaign.itemTemplateId() > 0
                ? List.of(new MailFacade.AttachmentRequest(campaign.itemTemplateId(), campaign.quantity()))
                : List.of();
        int sent = 0;
        int duplicate = 0;
        int failed = 0;
        for (long characterId : recipients) {
            MailFacade.SendOutcome outcome = deps.mail()
                    .sendMail(
                            new MailFacade.SendMailRequest(null, characterId, SUBJECT, BODY, campaign.gold(), items),
                            keyOf(campaign.code(), characterId));
            switch (outcome.result()) {
                case OK -> {
                    sent++;
                    notifyOnline(characterId, outcome.mailId());
                }
                case DUPLICATE_REQUEST -> duplicate++;
                default -> failed++;
            }
        }
        return new Tally(sent, duplicate, failed);
    }

    /** Khoá chống trùng tất định — cùng đợt + cùng người ⇒ cùng khoá. */
    static String keyOf(String code, long characterId) {
        return UUID.nameUUIDFromBytes(("gm-compensation:" + code + ":" + characterId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }

    private void notifyOnline(long characterId, long mailId) {
        deps.registry()
                .sendToCharacterIfOnline(
                        characterId,
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder().setMailId(mailId).build().toByteArray());
    }
}
