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
import dev.kage.protocol.SetGuildViceLeaderCommand;
import dev.kage.protocol.SetGuildViceLeaderOutcome;
import dev.kage.protocol.SetGuildViceLeaderResponse;
import dev.kage.protocol.TransferGuildLeaderCommand;
import dev.kage.protocol.TransferGuildLeaderOutcome;
import dev.kage.protocol.TransferGuildLeaderResponse;

/**
 * Nhường chức bang chủ (ADR-183) và bổ nhiệm / bãi nhiệm phó bang chủ (ADR-184) — server kiểm bang chủ / cùng bang /
 * trần 2 phó; xong thì báo mọi thành viên đang online.
 */
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
            notifyMembers(characterId, heir.id(), GuildChangeReason.GUILD_CHANGE_REASON_LEADER_CHANGED);
        }
        return outcome(
                TransferGuildLeaderOutcome.valueOf("TRANSFER_GUILD_LEADER_OUTCOME_" + result.name()), heir.name());
    }

    /** ADR-184 — Virtual Thread; {@code characterId} (bang chủ) từ session. */
    SetGuildViceLeaderResponse setVice(long characterId, SetGuildViceLeaderCommand command) {
        Optional<Character> target =
                deps.characters().findByName(command.getTargetName().strip());
        SetGuildViceLeaderResponse.Builder response =
                SetGuildViceLeaderResponse.newBuilder().setAppoint(command.getAppoint());
        if (target.isEmpty()) {
            return response.setOutcome(SetGuildViceLeaderOutcome.SET_GUILD_VICE_LEADER_OUTCOME_TARGET_NOT_IN_GUILD)
                    .build();
        }
        GuildLeadershipPort.ViceOutcome result = deps.leadership()
                .setViceLeader(
                        characterId,
                        target.get().id(),
                        command.getAppoint(),
                        deps.clock().instant());
        if (result == GuildLeadershipPort.ViceOutcome.OK) {
            notifyMembers(characterId, target.get().id(), GuildChangeReason.GUILD_CHANGE_REASON_VICE_CHANGED);
        }
        return response.setOutcome(SetGuildViceLeaderOutcome.valueOf("SET_GUILD_VICE_LEADER_OUTCOME_" + result.name()))
                .setTargetName(target.get().name())
                .build();
    }

    /** Báo mọi thành viên đang online trừ {@code actorId}; {@code subjectId} = người được nói tới. */
    private void notifyMembers(long actorId, long subjectId, GuildChangeReason reason) {
        byte[] payload = GuildChangedEvent.newBuilder()
                .setReason(reason)
                .setSubjectCharacterId(subjectId)
                .build()
                .toByteArray();
        for (GuildFacade.MemberView member : deps.guilds().listMembers(subjectId)) {
            if (member.characterId() != actorId) {
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
