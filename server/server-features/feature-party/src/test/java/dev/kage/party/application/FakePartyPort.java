package dev.kage.party.application;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import dev.kage.party.domain.DuplicatePartyInviteException;
import dev.kage.party.domain.DuplicatePartyMembershipException;
import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyDissolvedException;
import dev.kage.party.domain.PartyInvite;
import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;
import dev.kage.party.domain.PartyRole;

/**
 * Fake in-memory THẬT (TESTING.md §4), không Mockito — DÙNG CHUNG cho toàn bộ test
 * {@code application/} (cùng quyết định feature-guild — {@link PartyPort} nhiều method, lặp lại
 * nested Fake mỗi file test là trùng lặp thật). Logic mô phỏng đúng ràng buộc adapter Postgres
 * thật: UNIQUE (party_id, invitee_character_id) lời mời, UNIQUE character_id thành viên, khoá
 * sức chứa {@link Party#MAX_MEMBERS} lúc chấp nhận, thăng leader tự động lúc leader rời.
 */
class FakePartyPort implements PartyPort {

    private final Map<Long, Party> partiesById = new LinkedHashMap<>();
    private final List<PartyMember> members = new ArrayList<>();
    private final List<PartyInvite> invites = new ArrayList<>();
    private final AtomicLong nextPartyId = new AtomicLong(1);
    private final AtomicLong nextInviteId = new AtomicLong(1);

    @Override
    public Optional<PartyMember> findMembershipOf(long characterId) {
        return members.stream().filter(m -> m.characterId() == characterId).findFirst();
    }

    @Override
    public List<PartyMember> listMembers(long partyId) {
        return members.stream().filter(m -> m.partyId() == partyId).toList();
    }

    @Override
    public Party createParty(long leaderCharacterId) {
        // Mô phỏng UNIQUE party_member.character_id — KHÔNG gọi findMembershipOf (lớp con trong
        // CreatePartyUseCaseTest ghi đè nó để giả lập check-then-act thua race).
        if (members.stream().anyMatch(m -> m.characterId() == leaderCharacterId)) {
            throw new DuplicatePartyMembershipException(leaderCharacterId);
        }
        Party party = new Party(nextPartyId.getAndIncrement(), leaderCharacterId, Instant.now());
        partiesById.put(party.id(), party);
        members.add(new PartyMember(party.id(), leaderCharacterId, PartyRole.LEADER, Instant.now()));
        return party;
    }

    @Override
    public Optional<PartyInvite> findPendingInviteOfInvitee(long characterId) {
        return invites.stream()
                .filter(i -> i.inviteeCharacterId() == characterId)
                .findFirst();
    }

    @Override
    public List<PartyInvite> listPendingInvitesOfInvitee(long characterId) {
        return invites.stream()
                .filter(i -> i.inviteeCharacterId() == characterId)
                .toList();
    }

    @Override
    public int deleteInvitesInvitedBefore(Instant cutoff) {
        int before = invites.size();
        invites.removeIf(invite -> invite.invitedAt().isBefore(cutoff));
        return before - invites.size();
    }

    /** Lùi giờ gửi của MỌI lời mời đang chờ đi {@code age} — để test dựng một lời mời "cũ". */
    void ageInvitesBy(java.time.Duration age) {
        invites.replaceAll(invite -> new PartyInvite(
                invite.id(),
                invite.partyId(),
                invite.inviteeCharacterId(),
                invite.invitedAt().minus(age)));
    }

    @Override
    public PartyInvite invite(long partyId, long inviteeCharacterId) {
        // Mô phỏng FOREIGN KEY party_id — party đã bị giải tán.
        if (!partiesById.containsKey(partyId)) {
            throw new PartyDissolvedException(partyId);
        }
        if (invites.stream().anyMatch(i -> i.partyId() == partyId && i.inviteeCharacterId() == inviteeCharacterId)) {
            throw new DuplicatePartyInviteException(partyId, inviteeCharacterId);
        }
        PartyInvite invite =
                new PartyInvite(nextInviteId.getAndIncrement(), partyId, inviteeCharacterId, Instant.now());
        invites.add(invite);
        return invite;
    }

    @Override
    public AcceptResult acceptInvite(long inviteId, long characterId) {
        Optional<PartyInvite> invite = invites.stream()
                .filter(i -> i.id() == inviteId && i.inviteeCharacterId() == characterId)
                .findFirst();
        if (invite.isEmpty()) {
            return AcceptResult.NOT_FOUND;
        }
        if (findMembershipOf(characterId).isPresent()) {
            invites.remove(invite.get());
            return AcceptResult.ALREADY_IN_PARTY;
        }
        if (listMembers(invite.get().partyId()).size() >= Party.MAX_MEMBERS) {
            invites.remove(invite.get());
            return AcceptResult.PARTY_FULL;
        }
        members.add(new PartyMember(invite.get().partyId(), characterId, PartyRole.MEMBER, Instant.now()));
        invites.remove(invite.get());
        return AcceptResult.OK;
    }

    @Override
    public boolean rejectInvite(long inviteId, long characterId) {
        Optional<PartyInvite> invite = invites.stream()
                .filter(i -> i.id() == inviteId && i.inviteeCharacterId() == characterId)
                .findFirst();
        invite.ifPresent(invites::remove);
        return invite.isPresent();
    }

    @Override
    public LeaveResult leaveParty(long characterId) {
        Optional<PartyMember> membership = findMembershipOf(characterId);
        if (membership.isEmpty()) {
            return LeaveResult.NOT_IN_PARTY;
        }
        long partyId = membership.get().partyId();
        members.remove(membership.get());
        if (membership.get().role() != PartyRole.LEADER) {
            return LeaveResult.OK;
        }
        Optional<PartyMember> remaining = listMembers(partyId).stream()
                .sorted(Comparator.comparing(PartyMember::joinedAt))
                .findFirst();
        if (remaining.isEmpty()) {
            partiesById.remove(partyId);
            return LeaveResult.OK;
        }
        members.remove(remaining.get());
        members.add(new PartyMember(
                partyId,
                remaining.get().characterId(),
                PartyRole.LEADER,
                remaining.get().joinedAt()));
        return LeaveResult.OK;
    }

    @Override
    public boolean kickMember(long leaderCharacterId, long targetCharacterId) {
        Optional<PartyMember> leaderMembership = members.stream()
                .filter(m -> m.characterId() == leaderCharacterId && m.role() == PartyRole.LEADER)
                .findFirst();
        if (leaderMembership.isEmpty()) {
            return false;
        }
        long partyId = leaderMembership.get().partyId();
        return members.removeIf(
                m -> m.partyId() == partyId && m.characterId() == targetCharacterId && m.role() == PartyRole.MEMBER);
    }
}
