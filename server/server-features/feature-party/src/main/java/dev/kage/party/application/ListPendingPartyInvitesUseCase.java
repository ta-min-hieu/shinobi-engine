package dev.kage.party.application;

import java.util.List;

import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyPort;

/** Lời mời party đang chờ {@code characterId} (vai trò invitee) chấp nhận/từ chối. */
public final class ListPendingPartyInvitesUseCase {

    private final PartyPort port;

    public ListPendingPartyInvitesUseCase(PartyPort port) {
        this.port = port;
    }

    public List<PartyInvite> execute(long characterId) {
        return port.listPendingInvitesOfInvitee(characterId);
    }
}
