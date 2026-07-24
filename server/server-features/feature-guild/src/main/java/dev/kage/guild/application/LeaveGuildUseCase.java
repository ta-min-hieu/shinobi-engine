package dev.kage.guild.application;

import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;

/** Thành viên MEMBER tự rời bang — LEADER không được rời, phải {@link DisbandGuildUseCase}. */
public final class LeaveGuildUseCase {

    private final GuildPort port;

    public LeaveGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId) {
        var membership = port.findMembershipOf(characterId);
        if (membership.isEmpty()) {
            return Result.NOT_IN_GUILD;
        }
        if (membership.get().role() == GuildRole.LEADER) {
            return Result.LEADER_CANNOT_LEAVE;
        }
        port.leaveGuild(characterId);
        return Result.OK;
    }

    public enum Result { OK, NOT_IN_GUILD, LEADER_CANNOT_LEAVE }
}
