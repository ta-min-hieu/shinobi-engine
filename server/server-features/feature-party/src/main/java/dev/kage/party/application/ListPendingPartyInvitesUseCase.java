package dev.kage.party.application;

import java.time.Instant;
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
        // Lời mời hết hạn không còn là lời mời (2026-10-11) — dọn trước, rồi mới đọc.
        port.deleteInvitesInvitedBefore(PartyInvite.expiryCutoff(Instant.now()));
        return port.listPendingInvitesOfInvitee(characterId);
    }
}
