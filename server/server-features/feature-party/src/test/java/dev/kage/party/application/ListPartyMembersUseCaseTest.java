package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListPartyMembersUseCaseTest {

    @Test
    void shouldListAllMembers_includingLeader() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        var useCase = new ListPartyMembersUseCase(port);

        var members = useCase.execute(5);

        assertThat(members).extracting(m -> m.characterId()).containsExactlyInAnyOrder(1L, 5L);
    }

    @Test
    void shouldReturnEmpty_whenNotInAnyParty() {
        var useCase = new ListPartyMembersUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
