package dev.kage.guild.application;

import dev.kage.guild.domain.GuildPort;

/** Leader kick 1 thành viên MEMBER khỏi bang. {@code targetCharacterId} đã resolve từ tên ở gateway. */
public final class KickGuildMemberUseCase {

    private final GuildPort port;

    public KickGuildMemberUseCase(GuildPort port) {
        this.port = port;
    }

    public Result execute(long leaderCharacterId, long targetCharacterId) {
        if (leaderCharacterId == targetCharacterId) {
            return Result.CANNOT_KICK_SELF;
        }
        return port.kickMember(leaderCharacterId, targetCharacterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        CANNOT_KICK_SELF,
        NOT_FOUND
    }
}
