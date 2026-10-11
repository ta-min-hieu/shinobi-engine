package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyInvite;

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

    /**
     * Bug 2026-10-03: leader (người cuối) rời nhóm ở kết nối khác đúng giữa lúc use case xác nhận
     * "là leader" và lúc ghi lời mời — vi phạm FOREIGN KEY từng thành 500. Không còn party ⇒ không
     * còn là leader.
     */
    @Test
    void shouldReportNotLeader_whenPartyIsDissolvedBetweenLeaderCheckAndWrite() {
        FakePartyPort port = new FakePartyPort() {
            @Override
            public PartyInvite invite(long partyId, long inviteeCharacterId) {
                leaveParty(1);
                return super.invite(partyId, inviteeCharacterId);
            }
        };
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);

        InvitePartyMemberUseCase.Result result = useCase.execute(1, 5);

        assertThat(result).isEqualTo(InvitePartyMemberUseCase.Result.NOT_LEADER);
        assertThat(port.findPendingInviteOfInvitee(5)).isEmpty();
    }

    @Test
    void shouldReject_whenInviteAlreadyPending() {
        FakePartyPort port = new FakePartyPort();
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);
        useCase.execute(1, 5);

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.INVITE_ALREADY_PENDING);
    }

    /**
     * Mỗi người chỉ có MỘT lời mời chờ. Trước 2026-10-11 lời mời không hết hạn, nên một lời mời bị bỏ
     * quên chặn mọi nhóm khác mời người đó mãi mãi.
     */
    @Test
    void shouldInviteAgain_onceAnotherPartysInviteHasExpired() {
        FakePartyPort port = new FakePartyPort();
        Party otherParty = port.createParty(2);
        port.invite(otherParty.id(), 5);
        port.createParty(1);
        InvitePartyMemberUseCase useCase = new InvitePartyMemberUseCase(port);
        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.INVITE_ALREADY_PENDING);

        port.ageInvitesBy(PartyInvite.LIFETIME.plusSeconds(1));

        assertThat(useCase.execute(1, 5)).isEqualTo(InvitePartyMemberUseCase.Result.OK);
        assertThat(port.listPendingInvitesOfInvitee(5)).hasSize(1);
    }
}
