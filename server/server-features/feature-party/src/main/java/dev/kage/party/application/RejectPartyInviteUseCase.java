package dev.kage.party.application;

import dev.kage.party.domain.PartyPort;

/** Invitee từ chối lời mời — xoá dòng, không giữ lịch sử (cùng quyết định feature-friend/guild). */
public final class RejectPartyInviteUseCase {

    private final PartyPort port;

    public RejectPartyInviteUseCase(PartyPort port) {
        this.port = port;
    }

    public Result execute(long inviteId, long characterId) {
        return port.rejectInvite(inviteId, characterId) ? Result.OK : Result.NOT_FOUND;
    }

    public enum Result {
        OK,
        NOT_FOUND
    }
}
