package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ListPendingPartyInvitesUseCaseTest {

    @Test
    void shouldListInvitesAddressedToCharacter() {
        var port = new FakePartyPort();
        var partyA = port.createParty(1);
        var partyB = port.createParty(2);
        port.invite(partyA.id(), 5);
        port.invite(partyB.id(), 7);
        var useCase = new ListPendingPartyInvitesUseCase(port);

        assertThat(useCase.execute(5)).extracting(i -> i.inviteeCharacterId()).containsExactly(5L);
    }

    @Test
    void shouldReturnEmpty_whenNoInvitesPending() {
        var useCase = new ListPendingPartyInvitesUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
