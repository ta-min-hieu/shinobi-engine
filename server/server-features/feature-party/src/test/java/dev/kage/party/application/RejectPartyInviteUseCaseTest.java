package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;
import org.junit.jupiter.api.Test;

class RejectPartyInviteUseCaseTest {

    @Test
    void shouldReject_andDeleteInvite_whenCallerIsInvitee() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        RejectPartyInviteUseCase useCase = new RejectPartyInviteUseCase(port);

        RejectPartyInviteUseCase.Result result = useCase.execute(invite.id(), 5);

        assertThat(result).isEqualTo(RejectPartyInviteUseCase.Result.OK);
        assertThat(port.findPendingInviteOfInvitee(5)).isEmpty();
    }

    @Test
    void shouldReturnNotFound_whenCallerIsNotTheInvitee() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        PartyInvite invite = port.invite(party.id(), 5);
        RejectPartyInviteUseCase useCase = new RejectPartyInviteUseCase(port);

        RejectPartyInviteUseCase.Result result = useCase.execute(invite.id(), 999);

        assertThat(result).isEqualTo(RejectPartyInviteUseCase.Result.NOT_FOUND);
        assertThat(port.findPendingInviteOfInvitee(5)).isPresent();
    }
}
