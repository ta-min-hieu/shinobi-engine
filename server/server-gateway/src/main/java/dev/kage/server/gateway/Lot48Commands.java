package dev.kage.server.gateway;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.MarkMailReadCommand;
import dev.kage.protocol.MarkMailReadResponse;
import dev.kage.protocol.NewMailEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SendFriendsMailCommand;
import dev.kage.protocol.SendFriendsMailOutcome;
import dev.kage.protocol.SendFriendsMailResponse;
import dev.kage.protocol.SetGuildWelcomeCommand;
import dev.kage.protocol.SetGuildWelcomeOutcome;
import dev.kage.protocol.SetGuildWelcomeResponse;

/** Lớp lệnh lô 48 (ADR-311…) — MỘT thành phần của {@link Lot47Commands}. Gọi trên Virtual Thread. */
record Lot48Commands(GatewayFacades gateway, ConnectedCharacterRegistry registry) {

    /** Lớp lệnh lô 49 (ADR-319…) — record không trạng thái, dựng mỗi lần gọi. */
    Lot49Commands lot49() {
        return new Lot49Commands(gateway, registry);
    }

    /**
     * ADR-312 — một thư chữ (không vàng, không đồ) tới MỌI bạn bè, {@code FriendMailFacade.DAILY_LIMIT} đợt mỗi ngày.
     * Mỗi người nhận một thư thường (đúng đường gửi thư, tem thư, kiểm nội dung); ai đang online được báo thư mới.
     */
    SendFriendsMailResponse sendFriendsMail(long characterId, SendFriendsMailCommand command) {
        List<Long> friends =
                gateway.contentFacades().socialFacades().friendFacade().listFriends(characterId);
        if (friends.isEmpty()) {
            return friendsMail(SendFriendsMailOutcome.SEND_FRIENDS_MAIL_OUTCOME_NO_FRIENDS, 0);
        }
        Instant now = Instant.now();
        Instant dayStart = ResetSchedule.nextDaily(now).minus(Duration.ofDays(1));
        if (facades().friendMail().remaining(characterId, dayStart) == 0) {
            return friendsMail(SendFriendsMailOutcome.SEND_FRIENDS_MAIL_OUTCOME_DAILY_LIMIT, 0);
        }
        int sent = command.getSubject().isBlank() ? 0 : deliverAll(characterId, friends, command);
        if (sent == 0) {
            return friendsMail(SendFriendsMailOutcome.SEND_FRIENDS_MAIL_OUTCOME_INVALID_CONTENT, 0);
        }
        facades().friendMail().record(characterId, sent, now);
        return friendsMail(SendFriendsMailOutcome.SEND_FRIENDS_MAIL_OUTCOME_OK, sent);
    }

    private int deliverAll(long senderId, List<Long> friends, SendFriendsMailCommand command) {
        MailFacade mail = gateway.contentFacades().socialFacades().mailFacade();
        int sent = 0;
        for (long friend : friends) {
            MailFacade.SendOutcome outcome = mail.sendMail(new MailFacade.SendMailRequest(
                    senderId, friend, command.getSubject(), command.getBody(), 0L, List.of()));
            if (outcome.result() == MailFacade.SendResult.OK) {
                sent++;
                registry.sendToCharacterIfOnline(
                        friend,
                        Opcode.OPCODE_NEW_MAIL_EVENT_VALUE,
                        0,
                        NewMailEvent.newBuilder()
                                .setMailId(outcome.mailId())
                                .build()
                                .toByteArray());
            }
        }
        return sent;
    }

    private static SendFriendsMailResponse friendsMail(SendFriendsMailOutcome outcome, int recipients) {
        return SendFriendsMailResponse.newBuilder()
                .setOutcome(outcome)
                .setRecipients(recipients)
                .build();
    }

    /** ADR-313 — hỏi / đặt / xoá lời chào thành viên mới (chỉ bang chủ, phó bang chủ được đặt). */
    SetGuildWelcomeResponse setGuildWelcome(long characterId, SetGuildWelcomeCommand command) {
        Optional<GuildFacade.MyGuildView> mine =
                gateway.contentFacades().socialFacades().guildFacade().findMyGuild(characterId);
        if (mine.isEmpty()) {
            return welcome(SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_NOT_IN_GUILD, "");
        }
        long guildId = mine.get().guildId();
        if (command.getQueryOnly()) {
            return welcome(
                    SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_OK,
                    facades().guildWelcome().welcomeOf(guildId));
        }
        if (!mine.get().isLeader() && !mine.get().isViceLeader()) {
            return welcome(SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_NOT_OFFICER, "");
        }
        String message = command.getMessage().strip();
        if (!validWelcome(message)) {
            return welcome(SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_INVALID_TEXT, "");
        }
        facades().guildWelcome().setWelcome(guildId, message);
        return welcome(SetGuildWelcomeOutcome.SET_GUILD_WELCOME_OUTCOME_OK, message);
    }

    /** ≤ 200 ký tự, không ký tự điều khiển, không từ cấm (cùng bộ lọc chat). Tĩnh để test. */
    static boolean validWelcome(String message) {
        return message.length() <= dev.kage.guild.api.GuildWelcomeFacade.MAX_LENGTH
                && message.chars().noneMatch(Character::isISOControl)
                && !ProfanityFilter.containsBannedWord(message);
    }

    private static SetGuildWelcomeResponse welcome(SetGuildWelcomeOutcome outcome, String message) {
        return SetGuildWelcomeResponse.newBuilder()
                .setOutcome(outcome)
                .setMessage(message)
                .build();
    }

    /** ADR-314 — đánh dấu một thư của mình đã đọc; trả số thư còn chưa đọc. */
    MarkMailReadResponse markRead(long characterId, MarkMailReadCommand command) {
        facades().mailRead().markRead(command.getMailId(), characterId);
        return MarkMailReadResponse.newBuilder()
                .setMailId(command.getMailId())
                .setUnread(facades().mailRead().unreadIds(characterId).size())
                .build();
    }

    /** ADR-314 — id thư chưa đọc (cho danh sách thư). */
    Set<Long> unreadMailIds(long characterId) {
        return facades().mailRead().unreadIds(characterId);
    }

    /** ADR-314 — vào world: còn thư chưa đọc thì báo một dòng (kèm việc vào world của lô 50). */
    void onEnteredWorld(long characterId) {
        lot49().lot50().onEnteredWorld(characterId); // ADR-327 chuỗi đăng nhập, ADR-328 mạng lạ
        int unread = facades().mailRead().unreadIds(characterId).size();
        if (unread > 0) {
            registry.sendToCharacterIfOnline(
                    characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, unreadNotice(unread));
        }
    }

    /** Dòng báo thư chưa đọc. Tĩnh để test. */
    static byte[] unreadNotice(int unread) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Bạn có " + unread + " thư chưa đọc. / You have " + unread + " unread mail.")
                .build()
                .toByteArray();
    }

    private Lot48Facades facades() {
        return gateway.progression()
                .expansion()
                .lot34()
                .lot35()
                .lot36()
                .lot37()
                .lot38()
                .lot39()
                .lot40()
                .lot41()
                .lot42()
                .lot44()
                .lot45()
                .lot46()
                .lot48();
    }
}
