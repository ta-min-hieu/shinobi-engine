package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.Party;
import org.junit.jupiter.api.Test;

class InvitePartyMemberUseCaseTest {

    @Test
    void shouldInvite_whenLeaderAndTargetFree() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        InvitePartyMemberUseCase.Result result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(InvitePartyMemberUseCase.Result.OK);
        assertThat(port.findPendingInviteOfInvitee(5)).isPresent();
    }

    @Test
    void shouldRejectSelfInvite() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 1)).isEqualTo(InvitePartyMemberUseCase.Result.CANNOT_INVITE_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(5, 7)).isEqualTo(InvitePartyMemberUseCase.Result.NOT_LEADER);
    }

    @Test
    void shouldReject_whenTargetAlreadyInAParty() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        port.createParty(5);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.TARGET_ALREADY_IN_PARTY);
    }

    @Test
    void shouldReject_whenPartyAlreadyFull() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        for (long memberId = 2; memberId < Party.MAX_MEMBERS + 1; memberId++) {
            port.acceptInvite(port.invite(party.id(), memberId).id(), memberId);
        }
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 999)).isEqualTo(InvitePartyMemberUseCase.Result.PARTY_FULL);
    }

    @Test
    void shouldReject_whenInviteAlreadyPending() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);
        useCase.execute(1, 5);

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.INVITE_ALREADY_PENDING);
    }
}
