package dev.kage.party.application;

import java.util.List;

import dev.kage.party.domain.PartyMember;
import dev.kage.party.domain.PartyPort;

/** Danh sách thành viên party của {@code characterId} — rỗng nếu chưa vào party nào. */
public final class ListPartyMembersUseCase {

    private final PartyPort port;

    public ListPartyMembersUseCase(PartyPort port) {
        this.port = port;
    }

    public List<PartyMember> execute(long characterId) {
        return port.findMembershipOf(characterId)
                .map(member -> port.listMembers(member.partyId()))
                .orElse(List.of());
    }
}
