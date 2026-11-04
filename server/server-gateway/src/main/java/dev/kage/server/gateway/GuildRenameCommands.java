package dev.kage.server.gateway;

import java.time.Clock;

import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildRenameFacade;
import dev.kage.guild.domain.GuildRenamePort;
import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RenameGuildCommand;
import dev.kage.protocol.RenameGuildOutcome;
import dev.kage.protocol.RenameGuildResponse;

/** Đổi tên bang hội (ADR-172) — server kiểm bang chủ/tên/hồi chiêu/vàng; xong thì báo mọi thành viên đang online. */
final class GuildRenameCommands {

    /** Nguồn dữ liệu. */
    record Deps(GuildRenameFacade renames, GuildFacade guilds, ConnectedCharacterRegistry registry, Clock clock) {}

    private final Deps deps;

    GuildRenameCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} từ session. */
    RenameGuildResponse rename(long characterId, RenameGuildCommand command) {
        GuildRenamePort.Renamed renamed = deps.renames()
                .rename(characterId, command.getNewName(), deps.clock().instant());
        RenameGuildResponse.Builder response = RenameGuildResponse.newBuilder()
                .setOutcome(RenameGuildOutcome.valueOf(
                        "RENAME_GUILD_OUTCOME_" + renamed.outcome().name()))
                .setName(renamed.name())
                .setNextAllowedAtEpochMs(renamed.nextAllowedAt().toEpochMilli());
        if (renamed.outcome() == GuildRenamePort.Outcome.OK) {
            response.setFee(GuildRenamePort.FEE);
            notifyMembers(characterId);
        }
        return response.build();
    }

    private void notifyMembers(long characterId) {
        byte[] payload = GuildChangedEvent.newBuilder()
                .setReason(GuildChangeReason.GUILD_CHANGE_REASON_RENAMED)
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
}
