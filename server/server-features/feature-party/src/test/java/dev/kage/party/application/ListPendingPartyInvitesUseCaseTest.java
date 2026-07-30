package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;

class ListPendingPartyInvitesUseCaseTest {

    @Test
    void shouldListInvitesAddressedToCharacter() {
        FakePartyPort port = new FakePartyPort();
        Party partyA = port.createParty(1);
        Party partyB = port.createParty(2);
        port.invite(partyA.id(), 5);
        port.invite(partyB.id(), 7);
        ListPendingPartyInvitesUseCase useCase = new ListPendingPartyInvitesUseCase(port);

        assertThat(useCase.execute(5)).extracting(i -> i.inviteeCharacterId()).containsExactly(5L);
    }

    @Test
    void shouldReturnEmpty_whenNoInvitesPending() {
        ListPendingPartyInvitesUseCase useCase = new ListPendingPartyInvitesUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEmpty();
    }
}
