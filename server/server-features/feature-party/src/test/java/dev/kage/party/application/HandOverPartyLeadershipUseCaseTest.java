package dev.kage.party.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.kage.party.domain.Party;
import dev.kage.party.domain.PartyRole;

/**
 * Trưởng nhóm mất kết nối thì thành viên vào sớm nhất đang online lên thay (quyết định chủ dự án
 * 2026-10-11). Trước đó cả nhóm kẹt: không ai mời/kick được, và hầm ngục đòi cả nhóm online.
 */
class HandOverPartyLeadershipUseCaseTest {

    private static final long LEADER = 1L;
    private static final long FIRST_MEMBER = 5L;
    private static final long SECOND_MEMBER = 7L;

    private final FakePartyPort port = new FakePartyPort();
    private final HandOverPartyLeadershipUseCase useCase = new HandOverPartyLeadershipUseCase(port);

    @Test
    void shouldPromoteTheEarliestOnlineMember_andKeepTheOldLeaderInTheParty() {
        Party party = partyOfThree();

        Optional<Long> newLeader = useCase.execute(LEADER, Set.of(FIRST_MEMBER, SECOND_MEMBER)::contains);

        assertThat(newLeader).contains(FIRST_MEMBER);
        assertThat(port.findMembershipOf(FIRST_MEMBER).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
        assertThat(port.findMembershipOf(LEADER).orElseThrow().role())
                .as("người cũ vẫn ở trong nhóm, với vai thành viên")
                .isEqualTo(PartyRole.MEMBER);
        assertThat(port.listMembers(party.id())).hasSize(3);
    }

    /** Người vào sớm nhất cũng đang vắng mặt thì quyền đi tới người online kế tiếp. */
    @Test
    void shouldSkipMembersWhoAreOffline() {
        partyOfThree();

        Optional<Long> newLeader = useCase.execute(LEADER, Set.of(SECOND_MEMBER)::contains);

        assertThat(newLeader).contains(SECOND_MEMBER);
        assertThat(port.findMembershipOf(FIRST_MEMBER).orElseThrow().role()).isEqualTo(PartyRole.MEMBER);
    }

    /** Không ai khác online: nhóm giữ nguyên, chờ trưởng nhóm quay lại. */
    @Test
    void shouldChangeNothing_whenNoOtherMemberIsOnline() {
        partyOfThree();

        Optional<Long> newLeader = useCase.execute(LEADER, characterId -> false);

        assertThat(newLeader).isEmpty();
        assertThat(port.findMembershipOf(LEADER).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
    }

    /** Thành viên thường rớt mạng không làm đổi trưởng nhóm; người không ở nhóm nào cũng vậy. */
    @Test
    void shouldChangeNothing_whenTheDisconnectedCharacterIsNotALeader() {
        partyOfThree();

        assertThat(useCase.execute(FIRST_MEMBER, characterId -> true)).isEmpty();
        assertThat(useCase.execute(999L, characterId -> true)).isEmpty();
        assertThat(port.findMembershipOf(LEADER).orElseThrow().role()).isEqualTo(PartyRole.LEADER);
    }

    private Party partyOfThree() {
        Party party = port.createParty(LEADER);
        port.acceptInvite(port.invite(party.id(), FIRST_MEMBER).id(), FIRST_MEMBER);
        port.acceptInvite(port.invite(party.id(), SECOND_MEMBER).id(), SECOND_MEMBER);
        return party;
    }
}
