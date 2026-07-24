package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class KickPartyMemberUseCaseTest {

    @Test
    void shouldKick_whenCallerIsLeaderAndTargetIsMember() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        var useCase = new KickPartyMemberUseCase(port);

        var result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(KickPartyMemberUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldRejectSelfKick() {
        var port = new FakePartyPort();
        port.createParty(1);
        var useCase = new KickPartyMemberUseCase(port);

        assertThat(useCase.execute(1, 1)).isEqualTo(KickPartyMemberUseCase.Result.CANNOT_KICK_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        var port = new FakePartyPort();
        var party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        port.acceptInvite(port.invite(party.id(), 7).id(), 7);
        var useCase = new KickPartyMemberUseCase(port);

        var result = useCase.execute(5, 7); // 5 là MEMBER thường, không phải leader

        assertThat(result).isEqualTo(KickPartyMemberUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(7)).isPresent();
    }
}
