package dev.kage.server.gateway;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import dev.kage.guild.api.GuildEventFacade;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.domain.GuildEventPort;
import dev.kage.protocol.ChatChannel;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.Opcode;

/**
 * Nhắc lịch hoạt động bang (ADR-256): mỗi phút tìm buổi sắp bắt đầu trong 10 phút chưa nhắc, gửi một dòng chat hệ
 * thống cho mọi thành viên đang online của bang đó rồi đánh dấu đã nhắc.
 */
final class GuildEventReminder {

    private static final long PERIOD_SECONDS = 60L;
    private static final long SECONDS_PER_MINUTE = 60L;

    /** Nguồn dữ liệu. */
    record Deps(GuildEventFacade events, GuildFacade guilds, ConnectedCharacterRegistry registry, Clock clock) {}

    private GuildEventReminder() {}

    /** Việc định kỳ cho {@link ExpansionJobs}. */
    static ExpansionJobs.Job job(Deps deps) {
        return new ExpansionJobs.Job("nhắc lịch bang", PERIOD_SECONDS, () -> tick(deps));
    }

    static void tick(Deps deps) {
        Instant now = deps.clock().instant();
        for (GuildEventPort.Due due : deps.events().due(now)) {
            byte[] event = notice(due.title(), Duration.between(now, due.startsAt()));
            for (GuildFacade.MemberView member : deps.guilds().listMembers(due.leaderId())) {
                deps.registry()
                        .sendToCharacterIfOnline(
                                member.characterId(), Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE, 0, event);
            }
            deps.events().markReminded(due.id());
        }
    }

    /** Dòng chat song ngữ "còn N phút". Tĩnh để test. */
    static byte[] notice(String title, Duration left) {
        long minutes = Math.max(1L, (left.toSeconds() + SECONDS_PER_MINUTE - 1) / SECONDS_PER_MINUTE);
        return ChatMessageEvent.newBuilder()
                .setChannel(ChatChannel.CHAT_CHANNEL_UNSPECIFIED)
                .setSenderName("")
                .setBody("Lịch bang: \"" + title + "\" bắt đầu sau " + minutes + " phút. / Guild event: \"" + title
                        + "\" starts in " + minutes + " minutes.")
                .build()
                .toByteArray();
    }
}
