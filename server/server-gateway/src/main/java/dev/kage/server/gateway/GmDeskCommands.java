package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.gm.api.GmFacade;
import dev.kage.gm.domain.GiftCode;
import dev.kage.gm.domain.GmCommand;
import dev.kage.gm.domain.GmOutcome;
import dev.kage.gm.domain.PlayerReport;
import dev.kage.inventory.api.InventoryFacade;
import dev.kage.inventory.domain.ItemCatalogEntry;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.ServerNoticeEvent;

/**
 * Phần GM của lô 22 phần 1 ({@link GmCommand.Desk}) — {@code motd}, {@code code}, {@code reports}, {@code report},
 * {@code resolve}. Chỉ {@link GmCommandRouter} gọi, SAU khi đã kiểm quyền và mở dòng audit; mỗi nhánh chốt dòng audit
 * đó. Phản hồi tiếng Anh như mọi lệnh GM (ADR-052 Q4) — trừ ghi chú và trích chat của báo cáo: đó là NỘI DUNG
 * người chơi, GM cần đọc đúng nguyên văn.
 */
final class GmDeskCommands {

    /** Số báo cáo liệt kê mỗi lần {@code /gm reports}. */
    static final int REPORTS_PER_PAGE = 10;

    /** Trần một dòng trả lời — trích chat dài. */
    private static final int MAX_REPLY_LENGTH = 400;

    /** Nguồn dữ liệu. */
    record Deps(ServiceDeskFacades desk, InventoryFacade inventory, ConnectedCharacterRegistry registry) {}

    private final Deps deps;
    private final Clock clock;

    GmDeskCommands(Deps deps, Clock clock) {
        this.deps = deps;
        this.clock = clock;
    }

    /** Thi hành một lệnh bàn trực; {@code actorId} = nhân vật GM (từ session). */
    String run(GmCommand.Desk command, long actorId, GmFacade.AuditTicket ticket) {
        return switch (command) {
            case GmCommand.Motd motd -> motd(motd, actorId, ticket);
            case GmCommand.CreateGiftCode code -> createCode(code, actorId, ticket);
            case GmCommand.Reports _ -> reports(ticket);
            case GmCommand.ShowReport show -> showReport(show.reportId(), ticket);
            case GmCommand.ResolveReport resolve -> resolve(resolve.reportId(), actorId, ticket);
        };
    }

    private String motd(GmCommand.Motd motd, long actorId, GmFacade.AuditTicket ticket) {
        if (motd.clears()) {
            deps.desk().notice().clear();
            ticket.finish(GmOutcome.OK, "{}");
            return "Server notice cleared";
        }
        deps.desk().notice().set(motd.text(), actorId, clock.instant());
        deps.registry()
                .broadcastToAll(
                        Opcode.OPCODE_SERVER_NOTICE_EVENT_VALUE,
                        ServerNoticeEvent.newBuilder()
                                .setBody(motd.text())
                                .build()
                                .toByteArray());
        ticket.finish(GmOutcome.OK, "{}");
        return "Server notice set (" + motd.text().length() + " chars)";
    }

    private String createCode(GmCommand.CreateGiftCode command, long actorId, GmFacade.AuditTicket ticket) {
        long itemTemplateId = 0L;
        if (!command.itemCode().isEmpty()) {
            Optional<ItemCatalogEntry> item = deps.inventory().itemCatalog().entries().stream()
                    .filter(entry -> entry.code().equals(command.itemCode()))
                    .findFirst();
            if (item.isEmpty()) {
                ticket.finish(GmOutcome.REJECTED, "{}");
                return "No item '" + command.itemCode() + "'";
            }
            itemTemplateId = item.get().id();
        }
        Instant now = clock.instant();
        Instant expiresAt = now.plus(Duration.ofDays(command.days()));
        GiftCode code = new GiftCode(
                command.code(),
                command.gold(),
                itemTemplateId,
                itemTemplateId > 0 ? command.quantity() : 0,
                command.maxUses(),
                0,
                expiresAt);
        if (!deps.desk().giftCodes().create(code, actorId, now)) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "Code " + command.code() + " already exists";
        }
        ticket.finish(GmOutcome.OK, "{\"code\":\"" + command.code() + "\",\"expiresAt\":\"" + expiresAt + "\"}");
        return "Created code " + command.code() + " (" + command.maxUses() + " uses, until " + expiresAt + ")";
    }

    private String reports(GmFacade.AuditTicket ticket) {
        List<PlayerReport> open = deps.desk().reports().open(REPORTS_PER_PAGE);
        ticket.finish(GmOutcome.OK, "{}");
        if (open.isEmpty()) {
            return "No open reports";
        }
        StringBuilder line = new StringBuilder("Open reports:");
        for (PlayerReport report : open) {
            line.append(" #")
                    .append(report.id())
                    .append(' ')
                    .append(report.reason().label())
                    .append(" target=")
                    .append(report.targetId())
                    .append(" by=")
                    .append(report.reporterId())
                    .append(';');
        }
        return truncate(line.toString());
    }

    private String showReport(long reportId, GmFacade.AuditTicket ticket) {
        Optional<PlayerReport> report = deps.desk().reports().find(reportId);
        if (report.isEmpty()) {
            ticket.finish(GmOutcome.REJECTED, "{}");
            return "No report #" + reportId;
        }
        PlayerReport found = report.get();
        ticket.finish(GmOutcome.OK, "{}");
        return truncate("#%d %s target=%d by=%d note=%s chat=%s"
                .formatted(
                        found.id(),
                        found.reason().label(),
                        found.targetId(),
                        found.reporterId(),
                        found.note(),
                        found.chatExcerpt()));
    }

    private String resolve(long reportId, long actorId, GmFacade.AuditTicket ticket) {
        boolean resolved = deps.desk().reports().resolve(reportId, actorId);
        ticket.finish(resolved ? GmOutcome.OK : GmOutcome.REJECTED, "{}");
        return resolved ? "Resolved #" + reportId : "No open report #" + reportId;
    }

    private static String truncate(String reply) {
        return reply.length() <= MAX_REPLY_LENGTH ? reply : reply.substring(0, MAX_REPLY_LENGTH - 3) + "...";
    }
}
