package dev.kage.party.application;

import dev.kage.party.domain.PartyPort;

/** Leader kick 1 thành viên MEMBER khỏi party. {@code targetCharacterId} đã resolve từ tên ở gateway. */
public final class KickPartyMemberUseCase {

    private final PartyPort port;

    public KickPartyMemberUseCase(PartyPort port) {
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
