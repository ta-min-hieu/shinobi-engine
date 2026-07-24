package dev.kage.party.domain;

/** Ném khi adapter phát hiện vi phạm UNIQUE {@code (party_id, invitee_character_id)}. */
public final class DuplicatePartyInviteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public DuplicatePartyInviteException(long partyId, long inviteeCharacterId) {
        super("Đã có lời mời party: partyId=" + partyId + ", inviteeCharacterId=" + inviteeCharacterId);
    }
}
