package dev.kage.guild.application;

import dev.kage.guild.domain.GuildMember;
import dev.kage.guild.domain.GuildPort;
import dev.kage.guild.domain.GuildRole;
import java.util.Optional;

/** Leader giải tán bang — CASCADE xoá toàn bộ thành viên + đơn xin gia nhập đang chờ. */
public final class DisbandGuildUseCase {

    private final GuildPort port;

    public DisbandGuildUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long characterId) {
        Optional<GuildMember> membership = port.findMembershipOf(characterId);
        if (membership.isEmpty() || membership.get().role() != GuildRole.LEADER) {
            return Result.NOT_LEADER;
        }
        port.disbandGuild(characterId);
        return Result.OK;
    }

    public enum Result { OK, NOT_LEADER }
}
