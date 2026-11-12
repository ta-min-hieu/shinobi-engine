package dev.kage.server.gateway;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.friend.api.FriendFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.mail.api.ScheduledMailFacade;
import dev.kage.mail.api.StrangerMailFacade;
import dev.kage.mail.domain.ScheduledMailPort;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;

/**
 * Thư hẹn giờ (ADR-297) — mỗi phút gửi các thư đã tới giờ qua ĐÚNG đường gửi thư người chơi ({@link MailFacade}, có tem
 * thư), sau khi kiểm chặn thư người lạ (ADR-212) lúc gửi. Khoá chống trùng theo id dòng ⇒ server sập giữa chừng thì lần
 * quét sau không gửi đôi. Gửi được hay không đều xoá dòng (không thử lại vô hạn); không gửi được thì ghi log.
 */
final class ScheduledMailJob {

    private static final Logger LOG = LoggerFactory.getLogger(ScheduledMailJob.class);

    private static final long PERIOD_SECONDS = 60L;

    /** Nguồn dữ liệu. */
    record Deps(
            ScheduledMailFacade scheduled,
            MailFacade mail,
            StrangerMailFacade strangers,
            FriendFacade friends,
            ConnectedCharacterRegistry registry,
            Clock clock) {}

    private final Deps deps;

    ScheduledMailJob(Deps deps) {
        this.deps = deps;
    }

    /** Việc định kỳ cho {@link ExpansionJobs}. */
    static ExpansionJobs.Job job(Deps deps) {
        return new ExpansionJobs.Job("thư hẹn giờ", PERIOD_SECONDS, new ScheduledMailJob(deps)::tick);
    }

    void tick() {
        List<ScheduledMailPort.Pending> due = deps.scheduled().due(deps.clock().instant());
        for (ScheduledMailPort.Pending pending : due) {
            deliver(pending);
            deps.scheduled().delete(pending.id());
        }
    }

    private void deliver(ScheduledMailPort.Pending pending) {
        if (deps.strangers().refuses(pending.recipientId(), pending.senderId(), sender -> deps.friends()
                .listFriends(pending.recipientId())
                .contains(sender))) {
            LOG.info("Thư hẹn giờ {} bỏ: người nhận chặn thư người lạ", pending.id());
            return;
        }
        MailFacade.SendOutcome outcome = deps.mail()
                .sendMail(
                        new MailFacade.SendMailRequest(
                                pending.senderId(),
                                pending.recipientId(),
                                pending.subject(),
                                pending.body(),
                                0L,
                                List.of()),
                        key(pending.id()));
        if (outcome.result() == MailFacade.SendResult.OK) {
            deps.registry()
                    .sendToCharacterIfOnline(
                            pending.recipientId(),
                            Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                            0,
                            NewMailEvent.newBuilder()
                                    .setMailId(outcome.mailId())
                                    .build()
                                    .toByteArray());
        } else if (outcome.result() != MailFacade.SendResult.DUPLICATE_REQUEST) {
            LOG.info("Thư hẹn giờ {} không gửi được: {}", pending.id(), outcome.result());
        }
    }

    /** Khoá chống trùng (UUID tên) của một dòng. Tĩnh để test. */
    static String key(long scheduledId) {
        return UUID.nameUUIDFromBytes(("scheduled-mail:" + scheduledId).getBytes(StandardCharsets.UTF_8))
                .toString();
    }
}
