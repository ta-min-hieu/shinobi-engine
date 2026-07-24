package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RejectPartyInviteUseCaseTest {

    @Test
    void shouldReject_andDeleteInvite_whenCallerIsInvitee() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        var invite = port.invite(party.id(), 5);
        var useCase = new RejectPartyInviteUseCase(port);

        var result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(RejectPartyInviteUseCase.Result.OK);
        assertThat(port.findPendingInviteOfInvitee(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenCallerIsNotTheInvitee() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        var invite = port.invite(party.id(), 5);
        var useCase = new RejectPartyInviteUseCase(port);

        var result = useCase.execute(invite.id(), 999);

        assertThat(result).isEqualTo(RejectPartyInviteUseCase.Result.NOT_FOUND);
        assertThat(port.findPendingInviteOfInvitee(5)).isPresent();
    }
}
