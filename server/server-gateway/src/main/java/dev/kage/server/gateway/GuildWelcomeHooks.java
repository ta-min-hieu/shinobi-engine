package dev.kage.server.gateway;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildWelcomeFacade;
import dev.kage.mail.api.MailFacade;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Lời chào thành viên mới (ADR-313): ai vừa vào bang nhận lời chào bang chủ đã đặt — một dòng chat (nếu đang online)
 * và một thư hệ thống. Gọi từ {@link MembershipNotices} (luồng chặn được). Việc phụ: lỗi dừng ở đây. Chưa nối ⇒ không
 * làm gì (composition test).
 */
final class GuildWelcomeHooks {

    private static final Logger LOG = LoggerFactory.getLogger(GuildWelcomeHooks.class);
    private static final String SUBJECT = "Lời chào của bang / Guild welcome";

    /** Nguồn dữ liệu. */
    record Deps(GuildFacade guilds, GuildWelcomeFacade welcome, MailFacade mail, ConnectedCharacterRegistry registry) {}

    private volatile Deps deps;

    /** Nối ở composition root. */
    void bind(Deps bound) {
        this.deps = bound;
    }

    /** {@code characterId} vừa vào bang. */
    // PMD AvoidCatchingGenericException: việc phụ — lỗi không được làm hỏng lệnh vào bang.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    void onJoined(long characterId) {
        Deps bound = deps;
        if (bound == null) {
            return;
        }
        try {
            bound.guilds().findMyGuild(characterId).ifPresent(guild -> greet(bound, characterId, guild));
        } catch (RuntimeException e) {
            LOG.warn("Không gửi được lời chào bang: characterId={}", characterId, e);
        }
    }

    private static void greet(Deps bound, long characterId, GuildFacade.MyGuildView guild) {
        String message = bound.welcome().welcomeOf(guild.guildId());
        if (message.isBlank()) {
            return;
        }
        bound.registry()
                .sendToCharacterIfOnline(
                        characterId, Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, line(guild.guildName(), message));
        bound.mail().sendMail(new MailFacade.SendMailRequest(null, characterId, SUBJECT, message, 0L, List.of()));
    }

    /** Dòng chat lời chào. Tĩnh để test. */
    static byte[] line(String guildName, String message) {
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_GUILD)
                .setSenderName("")
                .setBody("[" + guildName + "] " + message)
                .build()
                .toByteArray();
    }
}
