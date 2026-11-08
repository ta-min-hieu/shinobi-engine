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
import dev.kage.protocol.CancelFriendRequestCommand;
import dev.kage.protocol.CancelFriendRequestResponse;
import dev.kage.protocol.FriendOfflineEvent;
import dev.kage.protocol.FriendOnlineEvent;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListPendingFriendRequestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.RejectFriendRequestOutcome;
import dev.kage.protocol.RejectFriendRequestResponse;
import dev.kage.protocol.RemoveFriendOutcome;
import dev.kage.protocol.RemoveFriendResponse;
import dev.kage.protocol.SendFriendGiftCommand;
import dev.kage.protocol.SendFriendGiftOutcome;
import dev.kage.protocol.SendFriendGiftResponse;
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

    private static final java.util.Map<SendFriendGiftOutcome, String> GIFT_KEYS = java.util.Map.of(
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_OK, "friend.notice.gifted",
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_TARGET_NOT_FOUND, "friend.error.target_not_found",
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_NOT_FRIENDS, "friend.error.not_friends",
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_ALREADY_GIFTED_TODAY, "friend.error.already_gifted",
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_INSUFFICIENT_GOLD, "friend.error.gift_no_gold",
            SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_CANNOT_GIFT_SELF, "friend.error.gift_self");

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

    private java.util.function.Predicate<String> favorite = name -> false; // ADR-276

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
        panel.setOnWithdrawRequest(this::onWithdrawRequest);
        panel.setOnGift(this::onGift);
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
        if (opcode == Opcode.OPCODE_SEND_FRIEND_GIFT_RESPONSE_VALUE) {
            SendFriendGiftResponse response = SendFriendGiftResponse.parseFrom(payload);
            settle(
                    GIFT_KEYS.getOrDefault(response.getOutcome(), "friend.error.unknown"),
                    response.getOutcome() == SendFriendGiftOutcome.SEND_FRIEND_GIFT_OUTCOME_OK);
            return true;
        }
        if (opcode == Opcode.OPCODE_CANCEL_FRIEND_REQUEST_RESPONSE_VALUE) {
            notice(FriendController.cancelOutcomeMessageKey(
                    CancelFriendRequestResponse.parseFrom(payload).getOutcome()));
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

    /** ADR-084 — cùng bộ lọc tên với gửi lời mời, rồi gửi lệnh tặng quà. */
    private void onGift(String typed) {
        FriendController.ComposeResult result = controller.compose(typed);
        switch (result) {
            case FriendController.ComposeResult.Send send ->
                sender.sendReadRequest(
                        Opcode.OPCODE_SEND_FRIEND_GIFT_COMMAND_VALUE,
                        SendFriendGiftCommand.newBuilder()
                                .setTargetName(send.targetName())
                                .build());
            case FriendController.ComposeResult.Reject reject -> notice(reject.messageKey());
            case FriendController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — không gửi gì.
            }
        }
    }

    /** ADR-074 — cùng bộ lọc tên với gửi lời mời, rồi gửi lệnh rút. */
    private void onWithdrawRequest(String typed) {
        FriendController.ComposeResult result = controller.compose(typed);
        switch (result) {
            case FriendController.ComposeResult.Send send ->
                sender.sendReadRequest(
                        Opcode.OPCODE_CANCEL_FRIEND_REQUEST_COMMAND_VALUE,
                        CancelFriendRequestCommand.newBuilder()
                                .setTargetName(send.targetName())
                                .build());
            case FriendController.ComposeResult.Reject reject -> notice(reject.messageKey());
            case FriendController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — không gửi gì.
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
        List<FriendController.Friend> friends = orderedFriends();
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

    /** ADR-276 — nối bạn yêu thích; vẽ lại ngay. */
    void useFavorites(java.util.function.Predicate<String> isFavorite) {
        this.favorite = isFavorite;
        refreshRows();
    }

    /**
     * ADR-276 — bạn yêu thích lên đầu (giữ thứ tự server trong mỗi nhóm). Dòng vẽ VÀ nút Xoá theo chỉ số dòng dùng
     * CÙNG danh sách này — lệch thứ tự là xoá nhầm người.
     */
    private List<FriendController.Friend> orderedFriends() {
        List<FriendController.Friend> ordered = new ArrayList<>();
        for (FriendController.Friend friend : controller.friends()) {
            if (favorite.test(friend.name())) {
                ordered.add(friend);
            }
        }
        for (FriendController.Friend friend : controller.friends()) {
            if (!favorite.test(friend.name())) {
                ordered.add(friend);
            }
        }
        return ordered;
    }

    /** Hỏi server cả hai danh sách — xem javadoc lớp về việc không có gói đẩy nào cho chuyện này. */
    private void refreshFromServer() {
        sender.listFriends();
        sender.listPendingFriendRequests();
    }

    private void refreshRows() {
        friendRows.clear();
        for (FriendController.Friend friend : orderedFriends()) {
            String label = withLastOnline(friend);
            if (favorite.test(friend.name())) {
                label = text("favorite.prefix") + " " + label; // ADR-276
            }
            friendRows.add(new FriendPanel.FriendRow(label, friend.online(), friend.intimacy()));
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

    /** ADR-192 — bạn đang offline thì kèm "(3 ngày trước)" sau tên. */
    private String withLastOnline(FriendController.Friend friend) {
        String ago = friend.online()
                ? ""
                : LastOnlineText.describe(
                        i18n,
                        controller.lastOnlineOf(friend.characterId()),
                        java.time.Instant.now().getEpochSecond());
        return ago.isEmpty() ? friend.displayName() : friend.displayName() + " (" + ago + ")";
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
