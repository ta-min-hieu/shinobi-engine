package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.FriendPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptFriendRequestOutcome;
import dev.kage.protocol.AcceptFriendRequestResponse;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RejectFriendRequestResponse;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.RemoveFriendResponse;
import dev.kage.protocol.SendFriendRequestOutcome;
import dev.kage.protocol.SendFriendRequestResponse;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Nối {@link FriendController} (trạng thái), {@link FriendPanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành cửa sổ bạn bè chạy được (2026-08-25) — cùng hình dạng
 * {@link ChatCoordinator}, và cùng lý do tồn tại: {@code WorldScreen} đang ở ĐÚNG trần
 * {@code CyclomaticComplexity} của PMD.
 *
 * <p>Toàn bộ dải opcode 1100-1113 <b>chưa từng được client dùng</b> trước hôm nay dù feature-friend
 * phía server đã xong từ Phase 6-7. Hai khoảng trống của giao thức phải xử lý ở đây:
 *
 * <ul>
 *   <li>Không có gói nào báo "danh sách bạn bè vừa đổi" — nên client hỏi lại cả hai danh sách mỗi
 *       lần cửa sổ mở ra và mỗi lần một thao tác của chính mình vừa thành công.
 *   <li>{@code FriendOnlineEvent}/{@code FriendOfflineEvent} chỉ mang {@code character_id}, không
 *       mang tên — người vừa online mà không có trong danh sách đang giữ thì client không có cách
 *       nào gọi tên họ, chỉ có cách hỏi lại (xem {@link #onPresenceChanged}).
 * </ul>
 */
final class FriendCoordinator implements WindowFlow {

    private final FriendController controller = new FriendController();
    private final FriendPanel panel;
    private final WorldCommandSender sender;

    /**
     * Kênh nói ra kết quả khi cửa sổ ĐANG ĐÓNG. Bạn bè vào/ra thế giới bất cứ lúc nào, và dòng
     * thông báo trong cửa sổ chỉ đọc được khi cửa sổ mở — chữ nổi giữa màn hình là kênh duy nhất
     * người chơi thấy trong lúc đang đánh nhau.
     */
    private final CombatFeedbackPresenter feedback;

    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link FriendPanel} — chỉ đổi khi danh sách đổi, không phải mỗi khung. */
    private final List<FriendPanel.FriendRow> friendRows = new ArrayList<>();

    private final List<FriendPanel.RequestRow> requestRows = new ArrayList<>();

    /**
     * @param canOpen lúc nào ĐƯỢC PHÉP mở cửa sổ bằng phím — xem
     *     {@link FriendPanel#setCanOpen}.
     */
    FriendCoordinator(
            FriendPanel panel, WorldCommandSender sender, CombatFeedbackPresenter feedback, BooleanSupplier canOpen) {
        this.panel = panel;
        this.sender = sender;
        this.feedback = feedback;
        panel.setCanOpen(canOpen);
        panel.setOnSendRequest(this::onSendRequest);
        panel.setOnAccept(this::onAccept);
        panel.setOnReject(this::onReject);
        panel.setOnRemove(this::onRemove);
        panel.setOnOpened(this::refreshFromServer);
    }

    /**
     * Cửa sổ đang mở hay không — {@code WorldScreen} phải chặn MỌI phím gameplay khi câu này đúng,
     * xem {@link FriendPanel#isOpen()}.
     */
    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng cửa sổ (mở hội thoại NPC, rời màn hình) — danh sách đang giữ vẫn nguyên. */
    @Override
    public void close() {
        panel.close();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng bạn bè.
     *
     * <p>Tách làm hai hàm THUẦN để hạ {@code CyclomaticComplexity} dưới ngưỡng PMD (8 opcode trong
     * một chuỗi if là quá ngưỡng), không phải đổi hành vi — cùng lối đi
     * {@code WorldScreen#dispatchMovement}.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE) {
            controller.onFriendList(ListFriendsResponse.parseFrom(payload));
            refreshRows();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_PENDING_FRIEND_REQUESTS_RESPONSE_VALUE) {
            controller.onPendingRequests(ListPendingFriendRequestsResponse.parseFrom(payload));
            refreshRows();
            return true;
        }
        if (opcode == Opcode.OPCODE_FRIEND_ONLINE_EVENT_VALUE) {
            onPresenceChanged(FriendOnlineEvent.parseFrom(payload).getCharacterId(), true);
            return true;
        }
        if (opcode == Opcode.OPCODE_FRIEND_OFFLINE_EVENT_VALUE) {
            onPresenceChanged(FriendOfflineEvent.parseFrom(payload).getCharacterId(), false);
            return true;
        }
        return dispatchOutcome(opcode, payload);
    }

    /** Bốn gói trả lời cho bốn lệnh của chính mình — xem {@link #dispatch}. */
    private boolean dispatchOutcome(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_SEND_FRIEND_REQUEST_RESPONSE_VALUE) {
            SendFriendRequestResponse response = SendFriendRequestResponse.parseFrom(payload);
            // "Đã là bạn" cũng là câu server trả khi lời mời NGƯỢC CHIỀU vừa được chấp nhận thay mình
            // (dòng 71(c), 2026-10-17) — danh sách trong tay khi đó đã cũ, nên hỏi lại.
            settle(
                    FriendController.sendOutcomeMessageKey(response.getOutcome()),
                    response.getOutcome() == SendFriendRequestOutcome.SEND_FRIEND_REQUEST_OUTCOME_ALREADY_FRIENDS);
            return true;
        }
        if (opcode == Opcode.OPCODE_ACCEPT_FRIEND_REQUEST_RESPONSE_VALUE) {
            AcceptFriendRequestResponse response = AcceptFriendRequestResponse.parseFrom(payload);
            settle(
                    FriendController.acceptOutcomeMessageKey(response.getOutcome()),
                    response.getOutcome() == AcceptFriendRequestOutcome.ACCEPT_FRIEND_REQUEST_OUTCOME_OK);
            return true;
        }
        if (opcode == Opcode.OPCODE_REJECT_FRIEND_REQUEST_RESPONSE_VALUE) {
            RejectFriendRequestResponse response = RejectFriendRequestResponse.parseFrom(payload);
            settle(
                    FriendController.rejectOutcomeMessageKey(response.getOutcome()),
                    response.getOutcome() == RejectFriendRequestOutcome.REJECT_FRIEND_REQUEST_OUTCOME_OK);
            return true;
        }
        if (opcode == Opcode.OPCODE_REMOVE_FRIEND_RESPONSE_VALUE) {
            RemoveFriendResponse response = RemoveFriendResponse.parseFrom(payload);
            settle(
                    FriendController.removeOutcomeMessageKey(response.getOutcome()),
                    response.getOutcome() == RemoveFriendOutcome.REMOVE_FRIEND_OUTCOME_OK);
            return true;
        }
        return false;
    }

    /**
     * Nói ra kết quả, và nếu thao tác THÀNH CÔNG thì hỏi lại danh sách.
     *
     * <p>Không tự sửa danh sách tại chỗ dù biết thừa mình vừa xoá ai: server là nguồn chân lý duy
     * nhất (Điều 15), và một lời mời được chấp nhận còn đổi CẢ HAI danh sách cùng lúc (lời mời biến
     * mất, người bạn mới xuất hiện kèm trạng thái online mà chỉ gateway biết).
     */
    private void settle(String messageKey, boolean succeeded) {
        notice(messageKey);
        if (succeeded) {
            refreshFromServer();
        }
    }

    /**
     * Một người bạn vừa vào/ra thế giới.
     *
     * <p>Không tra được tên nghĩa là danh sách trong tay đã cũ — VD ai đó vừa bấm Đồng ý với lời mời
     * của mình trong lúc mình đang chơi, và giao thức KHÔNG có gói nào báo chuyện đó. Hỏi lại danh
     * sách là cách duy nhất, và nó không thể thành vòng lặp: một gói {@code FriendOnlineEvent} kéo
     * theo đúng một {@code ListFriendsRequest}, còn gói trả lời không sinh ra gói nào nữa.
     */
    private void onPresenceChanged(long characterId, boolean online) {
        Optional<String> name = controller.onPresenceChanged(characterId, online);
        if (name.isEmpty()) {
            sender.listFriends();
            return;
        }
        refreshRows();
        // Tên người chơi khác đi thẳng vào chữ nổi — đã qua sanitize ở FriendController, nếu không
        // thì font UI im lặng nuốt mất ký tự và người chơi đọc được một cái tên cụt.
        feedback.showSelfText(
                i18n.format(online ? "friend.notice.online" : "friend.notice.offline", name.get()),
                online ? FloatingCombatText.LEVEL_UP : FloatingCombatText.MISS);
    }

    private void onSendRequest(String typed) {
        FriendController.ComposeResult result = controller.compose(typed);
        switch (result) {
            case FriendController.ComposeResult.Send send -> sender.sendFriendRequest(send.targetName());
            case FriendController.ComposeResult.Reject reject -> notice(reject.messageKey());
            case FriendController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — không gửi gì, và cũng không mắng người chơi.
            }
        }
    }

    private void onAccept(int rowIndex) {
        pendingRequestAt(rowIndex).ifPresent(request -> sender.acceptFriendRequest(request.friendshipId()));
    }

    private void onReject(int rowIndex) {
        pendingRequestAt(rowIndex).ifPresent(request -> sender.rejectFriendRequest(request.friendshipId()));
    }

    private void onRemove(int rowIndex) {
        List<FriendController.Friend> friends = controller.friends();
        if (rowIndex < 0 || rowIndex >= friends.size()) {
            return;
        }
        // Tên THẬT (chưa sanitize) — xem javadoc FriendController.Friend về lý do phải giữ cả hai.
        sender.removeFriend(friends.get(rowIndex).name());
    }

    /**
     * Dòng thứ {@code rowIndex} của mục lời mời, hoặc rỗng nếu danh sách đã đổi dưới chân cú bấm
     * (chỉ số do widget báo về, xem {@link FriendPanel}).
     */
    private Optional<FriendController.PendingRequest> pendingRequestAt(int rowIndex) {
        List<FriendController.PendingRequest> requests = controller.pendingRequests();
        if (rowIndex < 0 || rowIndex >= requests.size()) {
            return Optional.empty();
        }
        return Optional.of(requests.get(rowIndex));
    }

    /** Hỏi server cả hai danh sách — xem javadoc lớp về việc không có gói đẩy nào cho chuyện này. */
    private void refreshFromServer() {
        sender.listFriends();
        sender.listPendingFriendRequests();
    }

    private void refreshRows() {
        friendRows.clear();
        for (FriendController.Friend friend : controller.friends()) {
            friendRows.add(new FriendPanel.FriendRow(friend.displayName(), friend.online()));
        }
        requestRows.clear();
        for (FriendController.PendingRequest request : controller.pendingRequests()) {
            requestRows.add(new FriendPanel.RequestRow(request.requesterName()));
        }
        panel.setFriends(friendRows);
        panel.setPendingRequests(requestRows);
    }

    private void notice(String messageKey) {
        panel.setNotice(text(messageKey));
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        return KageI18n.textOrKey(i18n, key);
    }

    /** Rời màn hình/mất kết nối — danh sách của một phiên chơi không sống sót sang phiên sau. */
    @Override
    public void reset() {
        controller.clear();
        panel.close();
        refreshRows();
    }
}
