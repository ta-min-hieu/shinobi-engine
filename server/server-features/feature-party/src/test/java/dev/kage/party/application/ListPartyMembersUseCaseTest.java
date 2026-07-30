package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyMember;

class ListPartyMembersUseCaseTest {

    @Test
    void shouldListAllMembers_includingLeader() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        ListPartyMembersUseCase useCase = new ListPartyMembersUseCase(port);

        List<PartyMember> members = useCase.execute(5);

        assertThat(members).extracting(m -> m.characterId()).containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyParty() {
        ListPartyMembersUseCase useCase = new ListPartyMembersUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
