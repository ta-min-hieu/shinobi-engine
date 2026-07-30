package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;

class KickPartyMemberUseCaseTest {

    @Test
    void shouldKick_whenCallerIsLeaderAndTargetIsMember() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        KickPartyMemberUseCase useCase = new KickPartyMemberUseCase(port);

        KickPartyMemberUseCase.Result result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(KickPartyMemberUseCase.Result.OK);
        assertThat(port.findMembershipOf(5)).isEmpty();
    }

    @Test
    void shouldRejectSelfKick() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        KickPartyMemberUseCase useCase = new KickPartyMemberUseCase(port);

        assertThat(useCase.execute(1, 1)).isEqualTo(KickPartyMemberUseCase.Result.CANNOT_KICK_SELF);
    }

    @Test
    void shouldReject_whenCallerIsNotLeader() {
        FakePartyPort port = new FakePartyPort();
        Party party = port.createParty(1);
        port.acceptInvite(port.invite(party.id(), 5).id(), 5);
        port.acceptInvite(port.invite(party.id(), 7).id(), 7);
        KickPartyMemberUseCase useCase = new KickPartyMemberUseCase(port);

        KickPartyMemberUseCase.Result result = useCase.execute(5, 7); // 5 là MEMBER thường, không phải leader

        assertThat(result).isEqualTo(KickPartyMemberUseCase.Result.NOT_FOUND);
        assertThat(port.findMembershipOf(7)).isPresent();
    }
}
