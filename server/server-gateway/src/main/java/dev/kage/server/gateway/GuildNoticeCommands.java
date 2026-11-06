package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;

import dev.kage.chat.domain.ProfanityFilter;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildNoticeFacade;
import dev.kage.guild.domain.GuildNoticePort;
import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.SetGuildNoticeCommand;
import dev.kage.protocol.SetGuildNoticeOutcome;
import dev.kage.protocol.SetGuildNoticeResponse;

/**
 * Thông báo bang (ADR-179) — bang chủ ghi một dòng; server chuẩn hoá, áp bộ lọc từ cấm của chat, lưu, rồi báo mọi
 * thành viên đang online ({@code GuildChangedEvent} lý do {@code NOTICE_CHANGED} ⇒ client hỏi lại GetMyGuild).
 */
final class GuildNoticeCommands {

    /** Nguồn dữ liệu. */
    record Deps(GuildNoticeFacade notices, GuildFacade guilds, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;

    GuildNoticeCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    SetGuildNoticeResponse set(long characterId, SetGuildNoticeCommand command) {
        Optional<String> notice = GuildNoticeFacade.normalize(command.getNotice());
        if (notice.isEmpty()) {
            return outcome(SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_TOO_LONG);
        }
        if (ProfanityFilter.containsBannedWord(notice.get())) {
            return outcome(SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_BANNED_WORD);
        }
        GuildNoticePort.Outcome result =
                deps.notices().set(characterId, notice.get(), deps.clock().instant());
        if (result != GuildNoticePort.Outcome.OK) {
            return outcome(SetGuildNoticeOutcome.valueOf("SET_GUILD_NOTICE_OUTCOME_" + result.name()));
        }
        notifyMembers(characterId);
        return outcome(SetGuildNoticeOutcome.SET_GUILD_NOTICE_OUTCOME_OK).toBuilder()
                .setNotice(notice.get())
                .build();
    }

    /** Thông báo bang của người hỏi, "" nếu chưa có / không ở bang — cho GetMyGuildResponse.notice. */
    String noticeOf(long characterId) {
        return deps.notices().noticeOf(characterId).orElse("");
    }

    private void notifyMembers(long characterId) {
        byte[] payload = GuildChangedEvent.newBuilder()
                .setReason(GuildChangeReason.GUILD_CHANGE_REASON_NOTICE_CHANGED)
                .setSubjectCharacterId(characterId)
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : deps.guilds().listMembers(characterId)) {
            if (member.characterId() != characterId) {
                deps.registry()
                        .sendToCharacterIfOnline(
                                member.characterId(), Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE, 0, payload);
            }
        }
    }

    private static SetGuildNoticeResponse outcome(SetGuildNoticeOutcome outcome) {
        return SetGuildNoticeResponse.newBuilder().setOutcome(outcome).build();
    }
}
