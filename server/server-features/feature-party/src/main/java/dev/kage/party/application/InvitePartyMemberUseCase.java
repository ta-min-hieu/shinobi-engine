package dev.kage.party.application;

import java.util.Optional;

import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyDissolvedException;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;

/**
 * Leader mời 1 nhân vật theo ID đã resolve từ tên ở gateway (cùng lý do feature-friend — mời trực
 * tiếp, KHÔNG phải "xin gia nhập" như feature-guild).
 */
public final class InvitePartyMemberUseCase {

    private final PartyPort port;

    public InvitePartyMemberUseCase(PartyPort port) {
        this.port = port;
    }

    public Result execute(long leaderCharacterId, long targetCharacterId) {
        if (leaderCharacterId == targetCharacterId) {
            return Result.CANNOT_INVITE_SELF;
        }
        Optional<PartyMember> leaderMembership = port.findMembershipOf(leaderCharacterId);
        if (leaderMembership.isEmpty() || leaderMembership.get().role() != PartyRole.LEADER) {
            return Result.NOT_LEADER;
        }
        if (port.findMembershipOf(targetCharacterId).isPresent()) {
            return Result.TARGET_ALREADY_IN_PARTY;
        }
        long partyId = leaderMembership.get().partyId();
        if (port.listMembers(partyId).size() >= Party.MAX_MEMBERS) {
            return Result.PARTY_FULL;
        }
        if (port.findPendingInviteOfInvitee(targetCharacterId).isPresent()) {
            return Result.INVITE_ALREADY_PENDING;
        }
        try {
            port.invite(partyId, targetCharacterId);
        } catch (DuplicatePartyInviteException e) {
            return Result.INVITE_ALREADY_PENDING;
        } catch (PartyDissolvedException e) {
            // Leader (người cuối) rời nhóm ở kết nối khác ngay sau phép kiểm "là leader" ở trên
            // (2026-10-03, trước đó thành 500). Party không còn ⇒ người gọi không còn là leader.
            return Result.NOT_LEADER;
        }
        return Result.OK;
    }

    public enum Result {
        OK,
        NOT_LEADER,
        CANNOT_INVITE_SELF,
        TARGET_ALREADY_IN_PARTY,
        PARTY_FULL,
        INVITE_ALREADY_PENDING
    }
}
