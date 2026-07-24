package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.Party;
import org.junit.jupiter.api.Test;

class InvitePartyMemberUseCaseTest {

    @Test
    void shouldInvite_whenLeaderAndTargetFree() {
        var port = new FakePartyPort();
        port.createParty(1);
        var useCase = new InvitePartyMemberUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(InvitePartyMemberUseCase.Result.OK);
        assertThat(port.findPendingInviteOfInvitee(5)).isPresent();
    }

    @Test
    void shouldRejectSelfInvite() {
        var port = new FakePartyPort();
        port.createParty(1);
        var useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 1)).isEqualTo(InvitePartyMemberUseCase.Result.CANNOT_INVITE_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        var useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(5, 7)).isEqualTo(InvitePartyMemberUseCase.Result.NOT_LEADER);
    }

    @Test
    void shouldReject_whenTargetAlreadyInAParty() {
        var port = new FakePartyPort();
        port.createParty(1);
        port.createParty(5);
        var useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.TARGET_ALREADY_IN_PARTY);
    }

    @Test
    void shouldReject_whenPartyAlreadyFull() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        for (long memberId = 2; memberId < Party.MAX_MEMBERS + 1; memberId++) {
            port.acceptInvite(port.invite(party.id(), memberId).id(), memberId);
        }
        var useCase = new InvitePartyMemberUseCase(port);

        assertThat(useCase.execute(1, 999)).isEqualTo(InvitePartyMemberUseCase.Result.PARTY_FULL);
    }

    @Test
    void shouldReject_whenInviteAlreadyPending() {
        var port = new FakePartyPort();
        port.createParty(1);
        var useCase = new InvitePartyMemberUseCase(port);
        useCase.execute(1, 5);

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.INVITE_ALREADY_PENDING);
    }
}
