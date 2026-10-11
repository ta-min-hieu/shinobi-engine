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

    /** Lời mời hết hạn không được hiện ra cho người được mời (quyết định chủ dự án 2026-10-11). */
    @Test
    void shouldNotListAnExpiredInvite() {
        FakePartyPort port = new FakePartyPort();
        dev.kage.party.domain.Party party = port.createParty(1);
        port.invite(party.id(), 5);
        port.ageInvitesBy(dev.kage.party.domain.PartyInvite.LIFETIME.plusSeconds(1));

        assertThat(new ListPendingPartyInvitesUseCase(port).execute(5)).isEmpty();
    }
}
