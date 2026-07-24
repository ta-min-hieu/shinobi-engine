package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.kage.party.domain.PartyRole;
import org.junit.jupiter.api.Test;

class LeavePartyUseCaseTest {

    @Test
    void shouldLeave_whenMemberIsRegular() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        var useCase = new LeavePartyUseCase(port);

        var result = useCase.execute(5);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
        assertThat(port.findMembershipOf(1)).isPresent(); // leader vẫn còn nguyên
    }

    @Test
    void shouldReject_whenCharacterNotInAnyParty() {
        var useCase = new LeavePartyUseCase(new FakePartyPort());

        assertThat(useCase.execute(5)).isEqualTo(LeavePartyUseCase.Result.NOT_IN_PARTY);
    }

    @Test
    void shouldPromoteEarliestRemainingMember_whenLeaderLeaves() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        port.acceptInvite(port.invite(party.id(), 7).id(), 7);
        var useCase = new LeavePartyUseCase(port);

        var result = useCase.execute(1);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(1)).isEmpty();
        assertThat(port.findMembershipOf(5).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(port.findMembershipOf(7).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
    }

    @Test
    void shouldDissolveParty_whenLastMemberLeaves() {
        var port = new FakePartyPort();
        port.createParty(1);
        var useCase = new LeavePartyUseCase(port);

        var result = useCase.execute(1);

        assertThat(result).isEqualTo(LeavePartyUseCase.Result.OK);
        assertThat(port.findMembershipOf(1)).isEmpty();
    }
}
