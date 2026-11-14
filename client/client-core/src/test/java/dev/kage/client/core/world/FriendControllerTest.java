package dev.kage.client.core.world;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.PendingFriendRequestView;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.SendFriendRequestOutcome;

/**
 * {@link FriendController} thuần Java, không libGDX — chạy được thẳng trong JUnit như
 * {@code ChatControllerTest}.
 */
class FriendControllerTest {

    private final FriendController controller = new FriendController();

    @Test
    @DisplayName("Tên hợp lệ thành lệnh gửi lời mời, đã cắt khoảng trắng thừa")
    void composesTrimmedName() {
        assertThat(controller.compose("  HuongDan  ")).isEqualTo(new FriendController.ComposeResult.Send("HuongDan"));
    }

    @Test
    @DisplayName("Ô nhập rỗng/toàn khoảng trắng không gửi gì và cũng không báo lỗi")
    void ignoresBlankInput() {
        assertThat(controller.compose("")).isEqualTo(FriendController.ComposeResult.Nothing.INSTANCE);
        assertThat(controller.compose("    ")).isEqualTo(FriendController.ComposeResult.Nothing.INSTANCE);
    }

    @Test
    @DisplayName("Tên quá ngắn/quá dài bị chặn tại client, không tốn một gói tin")
    void rejectsNameOutsideServerLimits() {
        assertThat(controller.compose("a"))
                .isEqualTo(new FriendController.ComposeResult.Reject("friend.error.bad_name"));
        assertThat(controller.compose("x".repeat(FriendController.MAX_NAME_LENGTH + 1)))
                .isEqualTo(new FriendController.ComposeResult.Reject("friend.error.bad_name"));
    }

    @Test
    @DisplayName("Đúng hai đầu trần độ dài thì vẫn gửi được")
    void acceptsNameAtExactLimits() {
        String shortest = "x".repeat(FriendController.MIN_NAME_LENGTH);
        String longest = "x".repeat(FriendController.MAX_NAME_LENGTH);

        assertThat(controller.compose(shortest)).isEqualTo(new FriendController.ComposeResult.Send(shortest));
        assertThat(controller.compose(longest)).isEqualTo(new FriendController.ComposeResult.Send(longest));
    }

    @Test
    @DisplayName("Danh sách bạn xếp người đang online lên trước, rồi tới thứ tự chữ cái")
    void sortsOnlineFirst() {
        controller.onFriendList(friendList(friend(1L, "Bao", false), friend(2L, "Yen", true), friend(3L, "An", true)));

        assertThat(controller.friends())
                .extracting(FriendController.Friend::displayName)
                .containsExactly("An", "Yen", "Bao");
    }

    @Test
    @DisplayName("Danh sách mới THAY THẾ danh sách cũ (server luôn gửi trọn gói, không gửi delta)")
    void replacesPreviousList() {
        controller.onFriendList(friendList(friend(1L, "Bao", false)));

        controller.onFriendList(friendList(friend(2L, "Yen", true)));

        assertThat(controller.friends())
                .extracting(FriendController.Friend::characterId)
                .containsExactly(2L);
    }

    @Test
    @DisplayName("Tên bạn bè giữ NGUYÊN bản thật để gửi lên server, chỉ bản hiển thị bị sanitize")
    void keepsRealNameForTheWire() {
        controller.onFriendList(friendList(friend(1L, "Ninja世", true)));

        FriendController.Friend friend = controller.friends().get(0);
        // Gửi bản hiển thị lên server là gửi một cái tên KHÁC — người bạn này sẽ không bao giờ xoá
        // được (server trả TARGET_NOT_FOUND cho "Ninja?").
        assertThat(friend.name()).isEqualTo("Ninja世");
        assertThat(friend.displayName()).isEqualTo("Ninja?");
    }

    @Test
    @DisplayName("Tên người gửi lời mời cũng đi qua sanitize")
    void sanitizesRequesterName() {
        controller.onPendingRequests(pendingList(request(7L, "Kage界")));

        assertThat(controller.pendingRequests()).containsExactly(new FriendController.PendingRequest(7L, "Kage?", ""));
    }

    @Test
    @DisplayName("Gói online/offline vá đúng cột trạng thái và trả về tên để nói ra")
    void flipsPresenceOfKnownFriend() {
        controller.onFriendList(friendList(friend(1L, "Bao", false), friend(2L, "Yen", true)));

        assertThat(controller.onPresenceChanged(1L, true)).contains("Bao");

        assertThat(controller.friends())
                .filteredOn(friend -> friend.characterId() == 1L)
                .extracting(FriendController.Friend::online)
                .containsExactly(true);
    }

    @Test
    @DisplayName("Người lạ vừa online (vừa kết bạn xong) trả về rỗng — chỗ gọi phải hỏi lại danh sách")
    void reportsUnknownFriendOnPresenceEvent() {
        controller.onFriendList(friendList(friend(1L, "Bao", false)));

        assertThat(controller.onPresenceChanged(99L, true)).isEmpty();
    }

    @Test
    @DisplayName("Đổi trạng thái KHÔNG sắp xếp lại danh sách (dòng nhảy chỗ = bấm nhầm nút Xoá)")
    void keepsRowOrderAfterPresenceChange() {
        controller.onFriendList(friendList(friend(1L, "Bao", false), friend(2L, "Yen", true)));

        controller.onPresenceChanged(1L, true);

        assertThat(controller.friends())
                .extracting(FriendController.Friend::displayName)
                .containsExactly("Yen", "Bao");
    }

    @Test
    @DisplayName("Mọi kết quả của cả 4 lệnh đều có một câu để nói, kể cả OK")
    void mapsEveryOutcomeToAMessage() {
        for (SendFriendRequestOutcome outcome : SendFriendRequestOutcome.values()) {
            assertThat(FriendController.sendOutcomeMessageKey(outcome))
                    .as("outcome %s phải có khoá i18n", outcome)
                    .isNotBlank();
        }
        for (AcceptFriendRequestOutcome outcome : AcceptFriendRequestOutcome.values()) {
            assertThat(FriendController.acceptOutcomeMessageKey(outcome)).isNotBlank();
        }
        for (RejectFriendRequestOutcome outcome : RejectFriendRequestOutcome.values()) {
            assertThat(FriendController.rejectOutcomeMessageKey(outcome)).isNotBlank();
        }
        for (RemoveFriendOutcome outcome : RemoveFriendOutcome.values()) {
            assertThat(FriendController.removeOutcomeMessageKey(outcome)).isNotBlank();
        }
    }

    @Test
    @DisplayName("Kết quả server chưa từng biết (server mới hơn client) rơi vào câu 'không hiểu'")
    void fallsBackForUnrecognisedOutcome() {
        assertThat(FriendController.sendOutcomeMessageKey(SendFriendRequestOutcome.UNRECOGNIZED))
                .isEqualTo("friend.error.unknown");
        assertThat(FriendController.removeOutcomeMessageKey(RemoveFriendOutcome.UNRECOGNIZED))
                .isEqualTo("friend.error.unknown");
    }

    @Test
    @DisplayName("OK của mỗi lệnh là một câu KHÁC nhau — không dồn hết vào một thông báo chung")
    void distinguishesSuccessMessages() {
        assertThat(FriendController.sendOutcomeMessageKey(SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_OK))
                .isEqualTo("friend.notice.request_sent");
        assertThat(FriendController.acceptOutcomeMessageKey(
                        AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK))
                .isEqualTo("friend.notice.accepted");
        assertThat(FriendController.rejectOutcomeMessageKey(
                        RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK))
                .isEqualTo("friend.notice.rejected");
        assertThat(FriendController.removeOutcomeMessageKey(RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_OK))
                .isEqualTo("friend.notice.removed");
    }

    @Test
    @DisplayName("clear() xoá cả hai danh sách")
    void clearResetsEverything() {
        controller.onFriendList(friendList(friend(1L, "Bao", true)));
        controller.onPendingRequests(pendingList(request(7L, "Kage")));

        controller.clear();

        assertThat(controller.friends()).isEmpty();
        assertThat(controller.pendingRequests()).isEmpty();
    }

    private static FriendView friend(long characterId, String name, boolean online) {
        return FriendView.newBuilder()
                .setCharacterId(characterId)
                .setName(name)
                .setOnline(online)
                .build();
    }

    private static ListFriendsResponse friendList(FriendView... friends) {
        return ListFriendsResponse.newBuilder().addAllFriends(List.of(friends)).build();
    }

    private static PendingFriendRequestView request(long friendshipId, String requesterName) {
        return PendingFriendRequestView.newBuilder()
                .setFriendshipId(friendshipId)
                .setRequesterId(friendshipId)
                .setRequesterName(requesterName)
                .build();
    }

    private static ListPendingFriendRequestsResponse pendingList(PendingFriendRequestView... requests) {
        return ListPendingFriendRequestsResponse.newBuilder()
                .addAllRequests(List.of(requests))
                .build();
    }
}
