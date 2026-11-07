package dev.kage.server.gateway;

import java.time.Clock;
import java.util.Optional;

import dev.kage.character.api.CharacterFacade;
import dev.kage.character.domain.Character;
import dev.kage.guild.api.GuildFacade;
import dev.kage.guild.api.GuildLeadershipFacade;
import dev.kage.guild.domain.GuildLeadershipPort;
import dev.kage.protocol.GuildChangeReason;
import dev.kage.protocol.GuildChangedEvent;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderOutcome;
import dev.kage.protocol.TransferGuildLeaderResponse;

/** Nhường chức bang chủ (ADR-183) — server kiểm bang chủ / cùng bang; xong thì báo mọi thành viên đang online. */
final class GuildLeadershipCommands {

    /** Nguồn dữ liệu. */
    record Deps(
            GuildLeadershipFacade leadership,
            GuildFacade guilds,
            CharacterFacade characters,
            ConnectedCharacterRegistry registry,
            Clock clock) {}

    private final Deps deps;

    GuildLeadershipCommands(Deps deps) {
        this.deps = deps;
    }

    /** Virtual Thread; {@code characterId} (bang chủ hiện tại) từ session. */
    TransferGuildLeaderResponse transfer(long characterId, TransferGuildLeaderCommand command) {
        Optional<Character> target =
                deps.characters().findByName(command.getTargetName().strip());
        if (target.isEmpty()) {
            return outcome(TransferGuildLeaderOutcome.TRANSFER_GUILD_LEADER_OUTCOME_TARGET_NOT_IN_GUILD, "");
        }
        Character heir = target.get();
        GuildLeadershipPort.Outcome result =
                deps.leadership().transfer(characterId, heir.id(), deps.clock().instant());
        if (result == GuildLeadershipPort.Outcome.OK) {
            notifyMembers(characterId, heir.id());
        }
        return outcome(
                TransferGuildLeaderOutcome.valueOf("TRANSFER_GUILD_LEADER_OUTCOME_" + result.name()), heir.name());
    }

    private void notifyMembers(long oldLeaderId, long newLeaderId) {
        byte[] payload = GuildChangedEvent.newBuilder()
                .setReason(GuildChangeReason.GUILD_CHANGE_REASON_LEADER_CHANGED)
                .setSubjectCharacterId(newLeaderId)
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : deps.guilds().listMembers(newLeaderId)) {
            if (member.characterId() != oldLeaderId) {
                deps.registry()
                        .sendToCharacterIfOnline(
                                member.characterId(), Opcode.OPCODE_GUILD_CHANGED_EVENT_VALUE, 0, payload);
            }
        }
    }

    private static TransferGuildLeaderResponse outcome(TransferGuildLeaderOutcome outcome, String name) {
        return TransferGuildLeaderResponse.newBuilder()
                .setOutcome(outcome)
                .setTargetName(name)
                .build();
    }
}
