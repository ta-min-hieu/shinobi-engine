package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;

class AcceptPartyInviteUseCaseTest {

    @Test
    void shouldAccept_andAddMember() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        AcceptPartyInviteUseCase.Result result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isPresent();
    }

    @Test
    void shouldReturnNotFound_whenCallerIsNotTheInvitee() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        AcceptPartyInviteUseCase.Result result = useCase.execute(invite.id(), 999);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnAlreadyInParty_whenInviteeJoinedElsewhereMeanwhile() {
        FakePartyPort port = new FakePartyPort();
        Party partyA = port.createParty(1);
        PartyInvite invite = port.invite(partyA.id(), 5);
        port.createParty(5); // 5 tự tạo party khác trong lúc lời mời còn PENDING
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        AcceptPartyInviteUseCase.Result result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.ALREADY_IN_PARTY);
        assertThat(port.findPendingInviteOfInvitee(5)).isEmpty();
    }

    @Test
    void shouldReturnPartyFull_whenCapacityReachedByOtherAcceptsMeanwhile() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite lateInvite = port.invite(party.id(), 999);
        for (long memberId = 2; memberId < Party.MAX_MEMBERS + 1; memberId++) {
            port.acceptInvite(port.invite(party.id(), memberId).id(), memberId);
        }
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        AcceptPartyInviteUseCase.Result result = useCase.execute(lateInvite.id(), 999);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.PARTY_FULL);
    }

    /** Lời mời quá {@link PartyInvite#LIFETIME} không còn là lời mời (quyết định chủ dự án 2026-10-11). */
    @Test
    void shouldReturnNotFound_whenTheInviteHasExpired() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        port.ageInvitesBy(PartyInvite.LIFETIME.plusSeconds(1));
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        AcceptPartyInviteUseCase.Result result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldStillAccept_whenTheInviteIsAlmostExpired() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        port.ageInvitesBy(PartyInvite.LIFETIME.minusSeconds(30));
        AcceptPartyInviteUseCase useCase = new AcceptPartyInviteUseCase(port);

        assertThat(useCase.execute(invite.id(), 5)).isEqualTo(AcceptPartyInviteUseCase.Result.OK);
    }
}
