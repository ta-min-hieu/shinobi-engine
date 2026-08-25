package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptPartyInviteOutcome;
import dev.kage.protocol.CreatePartyOutcome;
import dev.kage.protocol.GetMyPartyResponse;
import dev.kage.protocol.InvitePartyMemberOutcome;
import dev.kage.protocol.KickPartyMemberOutcome;
import dev.kage.protocol.LeavePartyOutcome;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.ListPendingPartyInvitesResponse;
import dev.kage.protocol.PartyMemberView;
import dev.kage.protocol.PendingPartyInviteView;
import dev.kage.protocol.RejectPartyInviteOutcome;

/** {@link PartyController} thuần Java, không libGDX — như {@code ChatControllerTest}. */
class PartyControllerTest {

    private final PartyController controller = new PartyController();

    @Test
    @DisplayName("Khởi đầu: chưa vào nhóm nào, không thành viên, không lời mời")
    void startsOutsideAnyParty() {
        assertThat(controller.inParty()).isFalse();
        assertThat(controller.isLeader()).isFalse();
        assertThat(controller.members()).isEmpty();
        assertThat(controller.invites()).isEmpty();
    }

    @Test
    @DisplayName("Vào nhóm với vai trưởng nhóm")
    void tracksLeadership() {
        controller.onMyParty(myParty(true, true));

        assertThat(controller.inParty()).isTrue();
        assertThat(controller.isLeader()).isTrue();
    }

    @Test
    @DisplayName("is_leader=true mà in_party=false thì KHÔNG phải trưởng nhóm")
    void ignoresLeaderFlagWhenNotInParty() {
        // Không phải bắt bẻ vô cớ: party.proto nói party_id "chỉ có ý nghĩa khi in_party=true", nên
        // is_leader của một người không ở trong nhóm nào cũng không có nghĩa gì. Tin nó là bày ra nút
        // Mời cho người không có nhóm để mà mời.
        controller.onMyParty(myParty(false, true));

        assertThat(controller.inParty()).isFalse();
        assertThat(controller.isLeader()).isFalse();
    }

    @Test
    @DisplayName("Rời/bị đuổi khỏi nhóm thì danh sách thành viên cũ bị dọn sạch")
    void clearsMembersWhenLeavingParty() {
        controller.onMyParty(myParty(true, false));
        controller.onMembers(members(PartyMemberView.newBuilder()
                .setCharacterId(1L)
                .setName("KageHoa")
                .setIsLeader(true)
                .setOnline(true)
                .build()));
        assertThat(controller.members()).hasSize(1);

        // Server KHÔNG đẩy gì khi mình bị kick — chỉ biết ở lần hỏi tiếp theo. Nếu không dọn ở đây,
        // màn hình vẫn hiện đủ tên nhóm cũ dù đã ra khỏi nhóm.
        controller.onMyParty(myParty(false, false));

        assertThat(controller.members()).isEmpty();
    }

    @Test
    @DisplayName("Đọc thành viên: giữ cờ trưởng nhóm/trực tuyến và sanitize tên")
    void readsMembers() {
        controller.onMembers(members(
                PartyMemberView.newBuilder()
                        .setCharacterId(1L)
                        .setName("KageHoa")
                        .setIsLeader(true)
                        .setOnline(true)
                        .build(),
                PartyMemberView.newBuilder()
                        .setCharacterId(2L)
                        .setName("Thuy世")
                        .setIsLeader(false)
                        .setOnline(false)
                        .build()));

        assertThat(controller.members())
                .containsExactly(
                        new PartyController.Member(1L, "KageHoa", true, true),
                        // Tên do NGƯỜI KHÁC đặt — ký tự font UI không vẽ được phải thành '?', cùng
                        // lý do ChatController sanitize tin nhắn đến.
                        new PartyController.Member(2L, "Thuy?", false, false));
    }

    @Test
    @DisplayName("Đọc danh sách thành viên mới THAY THẾ danh sách cũ, không cộng dồn")
    void replacesMembersOnEachResponse() {
        controller.onMembers(members(
                PartyMemberView.newBuilder().setCharacterId(1L).setName("A").build()));
        controller.onMembers(members(
                PartyMemberView.newBuilder().setCharacterId(2L).setName("B").build()));

        assertThat(controller.members()).hasSize(1);
        assertThat(controller.members().get(0).name()).isEqualTo("B");
    }

    @Test
    @DisplayName("Đọc lời mời đang chờ")
    void readsPendingInvites() {
        controller.onInvites(ListPendingPartyInvitesResponse.newBuilder()
                .addInvites(PendingPartyInviteView.newBuilder()
                        .setInviteId(77L)
                        .setPartyId(5L)
                        .build())
                .build());

        assertThat(controller.invites()).containsExactly(new PartyController.Invite(77L, 5L));
    }

    @Test
    @DisplayName("Lệnh thành công không sinh lời nhắn nào")
    void successProducesNoMessage() {
        assertThat(PartyController.createOutcomeKey(CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK))
                .isEmpty();
        assertThat(PartyController.inviteOutcomeKey(InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_OK))
                .isEmpty();
        assertThat(PartyController.acceptOutcomeKey(AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_OK))
                .isEmpty();
        assertThat(PartyController.rejectOutcomeKey(RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK))
                .isEmpty();
        assertThat(PartyController.leaveOutcomeKey(LeavePartyOutcome.LEAVE_PARTY_OUTCOME_OK))
                .isEmpty();
        assertThat(PartyController.kickOutcomeKey(KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_OK))
                .isEmpty();
    }

    @Test
    @DisplayName("MỌI kết quả thất bại của cả 6 lệnh đều có lời giải thích")
    void everyFailureHasAMessage() {
        for (CreatePartyOutcome outcome : CreatePartyOutcome.values()) {
            assertKeyed(
                    outcome != CreatePartyOutcome.CREATE_PARTY_OUTCOME_OK,
                    PartyController.createOutcomeKey(outcome),
                    outcome);
        }
        for (InvitePartyMemberOutcome outcome : InvitePartyMemberOutcome.values()) {
            assertKeyed(
                    outcome != InvitePartyMemberOutcome.INVITE_PARTY_MEMBER_OUTCOME_OK,
                    PartyController.inviteOutcomeKey(outcome),
                    outcome);
        }
        for (AcceptPartyInviteOutcome outcome : AcceptPartyInviteOutcome.values()) {
            assertKeyed(
                    outcome != AcceptPartyInviteOutcome.ACCEPT_PARTY_INVITE_OUTCOME_OK,
                    PartyController.acceptOutcomeKey(outcome),
                    outcome);
        }
        for (RejectPartyInviteOutcome outcome : RejectPartyInviteOutcome.values()) {
            assertKeyed(
                    outcome != RejectPartyInviteOutcome.REJECT_PARTY_INVITE_OUTCOME_OK,
                    PartyController.rejectOutcomeKey(outcome),
                    outcome);
        }
        for (LeavePartyOutcome outcome : LeavePartyOutcome.values()) {
            assertKeyed(
                    outcome != LeavePartyOutcome.LEAVE_PARTY_OUTCOME_OK,
                    PartyController.leaveOutcomeKey(outcome),
                    outcome);
        }
        for (KickPartyMemberOutcome outcome : KickPartyMemberOutcome.values()) {
            assertKeyed(
                    outcome != KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_OK,
                    PartyController.kickOutcomeKey(outcome),
                    outcome);
        }
    }

    @Test
    @DisplayName("Lý do thất bại của lệnh đuổi nói ra CẢ HAI khả năng mà giao thức gộp lại")
    void kickNotFoundExplainsBothCauses() {
        // party.proto gộp "không phải leader" và "target không cùng nhóm" vào MỘT giá trị — không
        // tách được ở client, nên khoá phải là câu nói được cả hai.
        assertThat(PartyController.kickOutcomeKey(KickPartyMemberOutcome.KICK_PARTY_MEMBER_OUTCOME_NOT_FOUND))
                .contains("party.error.kick_not_allowed");
    }

    @Test
    @DisplayName("clear() đưa mọi thứ về trạng thái chưa vào nhóm")
    void clearResetsEverything() {
        controller.onMyParty(myParty(true, true));
        controller.onInvites(ListPendingPartyInvitesResponse.newBuilder()
                .addInvites(PendingPartyInviteView.newBuilder().setInviteId(1L).build())
                .build());

        controller.clear();

        assertThat(controller.inParty()).isFalse();
        assertThat(controller.isLeader()).isFalse();
        assertThat(controller.members()).isEmpty();
        assertThat(controller.invites()).isEmpty();
    }

    private static void assertKeyed(boolean shouldHaveKey, java.util.Optional<String> actual, Enum<?> outcome) {
        if (shouldHaveKey) {
            assertThat(actual).as("outcome %s phải có khoá i18n", outcome).isNotEmpty();
        }
    }

    private static GetMyPartyResponse myParty(boolean inParty, boolean isLeader) {
        return GetMyPartyResponse.newBuilder()
                .setInParty(inParty)
                .setIsLeader(isLeader)
                .setPartyId(inParty ? 9L : 0L)
                .build();
    }

    private static ListPartyMembersResponse members(PartyMemberView... views) {
        ListPartyMembersResponse.Builder builder = ListPartyMembersResponse.newBuilder();
        for (PartyMemberView view : views) {
            builder.addMembers(view);
        }
        return builder.build();
    }
}
