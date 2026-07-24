package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.Party;
import org.junit.jupiter.api.Test;

class AcceptPartyInviteUseCaseTest {

    @Test
    void shouldAccept_andAddMember() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        var invite = port.invite(party.id(), 5);
        var useCase = new AcceptPartyInviteUseCase(port);

        var result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isPresent();
    }

    @Test
    void shouldReturnNotFound_whenCallerIsNotTheInvitee() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        var invite = port.invite(party.id(), 5);
        var useCase = new AcceptPartyInviteUseCase(port);

        var result = useCase.execute(invite.id(), 999);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.NOT_FOUND);
    }

    @Test
    void shouldReturnAlreadyInParty_whenInviteeJoinedElsewhereMeanwhile() {
        var port = new FakePartyPort();
        var partyA = port.createParty(1);
        var invite = port.invite(partyA.id(), 5);
        port.createParty(5); // 5 tự tạo party khác trong lúc lời mời còn PENDING
        var useCase = new AcceptPartyInviteUseCase(port);

        var result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.ALREADY_IN_PARTY);
        assertThat(port.findPendingInviteOfInvitee(5)).isEmpty();
    }

    @Test
    void shouldReturnPartyFull_whenCapacityReachedByOtherAcceptsMeanwhile() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        var lateInvite = port.invite(party.id(), 999);
        for (long memberId = 2; memberId < Party.MAX_MEMBERS + 1; memberId++) {
            port.acceptInvite(port.invite(party.id(), memberId).id(), memberId);
        }
        var useCase = new AcceptPartyInviteUseCase(port);

        var result = useCase.execute(lateInvite.id(), 999);

        assertThat(result).isEqualTo(AcceptPartyInviteUseCase.Result.PARTY_FULL);
    }
}
