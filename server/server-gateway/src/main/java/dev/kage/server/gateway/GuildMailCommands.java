package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildMailFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendGuildMailCommand;
import dev.kage.protocol.SendGuildMailOutcome;
import dev.kage.protocol.SendGuildMailResponse;

/**
 * Thư toàn bang (ADR-235): bang chủ / phó bang chủ gửi MỘT thư (không vàng, không đồ) tới mọi thành viên khác, tối đa
 * {@value GuildMailFacade#DAILY_LIMIT} đợt mỗi bang mỗi ngày. Mỗi người nhận một thư thường (đi đúng đường gửi thư,
 * cùng kiểm nội dung); ai đang online được báo thư mới. Gọi trên Virtual Thread.
 */
final class GuildMailCommands {

    /** Nguồn dữ liệu. */
    record Deps(GatewayFacades gateway, GuildMailFacade quota, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;

    GuildMailCommands(Deps deps) {
        this.deps = deps;
    }

    SendGuildMailResponse send(long characterId, SendGuildMailCommand command) {
        Optional<GuildFacade.MyGuildView> mine = guilds().findMyGuild(characterId);
        Optional<SendGuildMailOutcome> refused = refusal(mine);
        if (refused.isPresent()) {
            return outcome(refused.get(), 0, 0);
        }
        Instant now = deps.clock().instant();
        long guildId = mine.orElseThrow().guildId();
        int remaining =
                deps.quota().remaining(guildId, ResetSchedule.nextDaily(now).minus(Duration.ofDays(1)));
        if (remaining == 0) {
            return outcome(SendGuildMailOutcome.SEND_GUILD_MAIL_OUTCOME_DAILY_LIMIT, 0, 0);
        }
        int sent = command.getSubject().isBlank() || command.getBody().isBlank() ? 0 : deliverAll(characterId, command);
        if (sent == 0) {
            return outcome(SendGuildMailOutcome.SEND_GUILD_MAIL_OUTCOME_INVALID_CONTENT, 0, remaining);
        }
        deps.quota().record(guildId, characterId, sent, now);
        return outcome(SendGuildMailOutcome.SEND_GUILD_MAIL_OUTCOME_OK, sent, remaining - 1);
    }

    /** Không có bang / không phải bang chủ hay phó ⇒ lý do từ chối. */
    private static Optional<SendGuildMailOutcome> refusal(Optional<GuildFacade.MyGuildView> mine) {
        if (mine.isEmpty()) {
            return Optional.of(SendGuildMailOutcome.SEND_GUILD_MAIL_OUTCOME_NOT_IN_GUILD);
        }
        return mine.get().isLeader() || mine.get().isViceLeader()
                ? Optional.empty()
                : Optional.of(SendGuildMailOutcome.SEND_GUILD_MAIL_OUTCOME_NOT_OFFICER);
    }

    private int deliverAll(long senderId, SendGuildMailCommand command) {
        int sent = 0;
        for (GuildFacade.MemberView member : guilds().listMembers(senderId)) {
            if (member.characterId() != senderId && deliver(senderId, member.characterId(), command)) {
                sent++;
            }
        }
        return sent;
    }

    private GuildFacade guilds() {
        return deps.gateway().contentFacades().socialFacades().guildFacade();
    }

    private boolean deliver(long senderId, long recipientId, SendGuildMailCommand command) {
        MailFacade.SendOutcome outcome = deps.gateway()
                .contentFacades()
                .socialFacades()
                .mailFacade()
                .sendMail(new MailFacade.SendMailRequest(
                        senderId, recipientId, command.getSubject(), command.getBody(), 0L, List.of()));
        if (outcome.result() != MailFacade.SendResult.OK) {
            return false;
        }
        deps.registry()
                .sendToCharacterIfOnline(
                        recipientId,
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
        return true;
    }

    private static SendGuildMailResponse outcome(SendGuildMailOutcome outcome, int recipients, int remaining) {
        return SendGuildMailResponse.newBuilder()
                .setOutcome(outcome)
                .setRecipients(recipients)
                .setRemainingToday(remaining)
                .build();
    }
}
