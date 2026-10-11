package dev.kage.party.application;

import java.time.Instant;

import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyPort;

/** Invitee chấp nhận lời mời — thêm thành viên MỚI + xoá lời mời, nguyên tử ở {@link PartyPort}. */
public final class AcceptPartyInviteUseCase {

    private final PartyPort port;

    public AcceptPartyInviteUseCase(PartyPort port) {
        this.port = port;
    }

    public Result execute(long inviteId, long characterId) {
        // Lời mời hết hạn không còn là lời mời (2026-10-11) — dọn trước, rồi mới đọc.
        port.deleteInvitesInvitedBefore(PartyInvite.expiryCutoff(Instant.now()));
        return switch (port.acceptInvite(inviteId, characterId)) {
            case OK -> Result.OK;
            case NOT_FOUND -> Result.NOT_FOUND;
            case ALREADY_IN_PARTY -> Result.ALREADY_IN_PARTY;
            case PARTY_FULL -> Result.PARTY_FULL;
        };
    }

    public enum Result {
        OK,
        NOT_FOUND,
        ALREADY_IN_PARTY,
        PARTY_FULL
    }
}
