package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyRole;

class LeavePartyUseCaseTest {

    @Test
    void shouldLeave_whenMemberIsRegular() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        LeavePartyUseCase useCase = new LeavePartyUseCase(port);

        LeavePartyUseCase.Result result = useCase.execute(5);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
        assertThat(port.findMembershipOf(1)).isPresent(); // leader vẫn còn nguyên
    }

    @Test
    void shouldReject_whenCharacterNotInAnyParty() {
        LeavePartyUseCase useCase = new LeavePartyUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEqualTo(LeavePartyUseCase.Result.NOT_IN_PARTY);
    }

    @Test
    void shouldPromoteEarliestRemainingMember_whenLeaderLeaves() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        port.acceptInvite(port.invite(party.id(), 7).id(), 7);
        LeavePartyUseCase useCase = new LeavePartyUseCase(port);

        LeavePartyUseCase.Result result = useCase.execute(1);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(1)).isEmpty();
        assertThat(port.findMembershipOf(5).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(port.findMembershipOf(7).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
    }

    @Test
    void shouldDissolveParty_whenLastMemberLeaves() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        LeavePartyUseCase useCase = new LeavePartyUseCase(port);

        LeavePartyUseCase.Result result = useCase.execute(1);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(1)).isEmpty();
    }
}
