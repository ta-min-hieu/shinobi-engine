package dev.kage.client.core.world;

import java.util.Optional;

import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.DuelPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptDuelResponse;
import dev.kage.protocol.ChallengeDuelOutcome;
import dev.kage.protocol.ChallengeDuelResponse;
import dev.kage.protocol.ChatMessageEvent;
import dev.kage.protocol.DeclineDuelResponse;
import dev.kage.protocol.DuelChallengeDeclinedEvent;
import dev.kage.protocol.DuelChallengeReceivedEvent;
import dev.kage.protocol.DuelResultEvent;
import dev.kage.protocol.DuelStartedEvent;
import dev.kage.protocol.ExitDuelResponse;
import dev.kage.protocol.FriendView;
import dev.kage.protocol.GetPvpRatingResponse;
import dev.kage.protocol.GuildMemberView;
import dev.kage.protocol.ListFriendsResponse;
import dev.kage.protocol.ListGuildMembersResponse;
import dev.kage.protocol.ListPartyMembersResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.PartyMemberView;

/**
 * Nối {@link DuelController} (trạng thái), {@link DuelPanel} + {@link CombatHud} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành luồng đấu tay đôi chạy được (2026-08-27) — cùng hình dạng
 * {@link GuildCoordinator}, và cùng lý do tồn tại: {@code WorldScreen} đang ở ĐÚNG trần
 * {@code CyclomaticComplexity} của PMD.
 *
 * <p>Toàn bộ 14 opcode của arena.proto (1600-1611 + 1200-1201) chưa từng được client dùng trước hôm
 * nay, dù {@code feature-pvp} phía server đã xong từ Phase 8.
 *
 * <p><b>Luật một dòng của lớp này: chỉ server mới đổi được trạng thái trận đấu.</b> Không một
 * {@code *Response} nào ở đây bật cờ "đang đấu" — kể cả {@code AcceptDuelResponse} với outcome
 * {@code OK}. Trận đấu chỉ bắt đầu khi {@code DuelStartedEvent} tới (arena.proto: "đẩy cho CẢ 2
 * người khi trận đấu bắt đầu thật, đã vào World riêng"), và chỉ kết thúc khi
 * {@code DuelResultEvent} tới. Điểm xếp hạng cũng vậy: client không cộng trừ gì, chỉ hỏi lại
 * ({@link #refreshRating}) và đọc số server đưa (Điều 15).
 *
 * <p>Lớp này cũng <b>nghe lỏm</b> bốn gói KHÔNG thuộc arena (chat, danh sách bạn/nhóm/bang) để nhặt
 * các cặp (id, tên) — xem {@link #learnNames}. Nghe lỏm chứ không chiếm: những gói đó vẫn trả
 * {@code false} để coordinator thật của chúng xử lý bình thường.
 */
final class DuelCoordinator {

    private final DuelController controller = new DuelController();
    private final DuelPanel panel;
    private final CombatHud hud;
    private final WorldCommandSender sender;

    /**
     * Kênh nói ra khi cửa sổ ĐANG ĐÓNG. Lời thách đấu và kết quả trận ập tới lúc người chơi không
     * bấm gì cả; chữ nổi giữa màn hình là kênh duy nhất họ thấy trong lúc đang đánh nhau — cùng lý
     * do {@code FriendCoordinator} mượn nó cho sự kiện bạn bè vào/ra.
     */
    private final CombatFeedbackPresenter feedback;

    private final I18NBundle i18n = KageI18n.load();

    DuelCoordinator(DuelPanel panel, CombatHud hud, WorldCommandSender sender, CombatFeedbackPresenter feedback) {
        this.panel = panel;
        this.hud = hud;
        this.sender = sender;
        this.feedback = feedback;
        panel.setOnOpened(this::refreshRating);
        panel.setOnChallengeCandidate(this::challengeCandidate);
        panel.setOnChallengeTyped(this::challengeTypedName);
        panel.setOnAccept(this::acceptIncoming);
        panel.setOnDecline(this::declineIncoming);
        panel.setOnForfeit(sender::exitDuel);
        panel.setStatus(text("duel.hint.open"));
        refreshView();
    }

    /**
     * Cửa sổ có đang mở không — {@code WorldScreen} phải chặn MỌI phím gameplay khi câu này đúng,
     * xem {@link DuelPanel#isOpen()}.
     */
    boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng cửa sổ (mở hội thoại NPC, rời màn hình) — trạng thái trận vẫn giữ nguyên. */
    void close() {
        panel.close();
    }

    /**
     * Người chơi vừa click vào một nhân vật khác trên màn hình ({@code WorldScreen}).
     *
     * <p>KHÔNG gửi lời thách đấu ngay: một cú click trượt trong lúc đánh nhau mà thành lời thách đấu
     * là thứ không rút lại được. Click chỉ CHỌN; gửi đi là một lần bấm nút nữa, cùng nhịp với việc
     * click quái chỉ đặt mục tiêu chứ không tự đánh ({@code AttackInputController}, ADR-034).
     *
     * @param characterId {@code 0} = cú click không trúng người chơi nào ({@code WorldEntityPicker}),
     *     tức nó còn phải rơi tiếp xuống chỗ khác — không phải việc của luồng đấu tay đôi.
     * @return {@code true} nếu đã nhận cú click này. Nhận CẢ khi chưa biết tên: lúc đó vẫn phải nói
     *     cho người chơi biết vì sao không thách đấu được, im lặng mới là hỏng.
     */
    boolean onCharacterPicked(long characterId) {
        if (characterId == 0L) {
            return false;
        }
        Optional<DuelController.Player> picked = controller.selectCandidate(characterId);
        if (picked.isEmpty()) {
            // Khoảng trống #1 của giao thức — xem javadoc DuelController. Nói thẳng ra thay vì để
            // người chơi click mãi vào một người mà không có gì xảy ra.
            feedback.showSelfNotice("duel.error.unknown_name", FloatingCombatText.MISS);
            return true;
        }
        feedback.showSelfText(format("duel.notice.selected", picked.get().displayName()), FloatingCombatText.LEVEL_UP);
        refreshView();
        return true;
    }

    /**
     * Định tuyến một gói có thể thuộc luồng đấu tay đôi.
     *
     * <p>Tách làm ba hàm THUẦN để hạ {@code CyclomaticComplexity} dưới ngưỡng PMD (14 opcode trong
     * một chuỗi if là quá xa ngưỡng), không phải đổi hành vi — cùng lối đi
     * {@code GuildCoordinator#dispatch}.
     *
     * @return {@code true} nếu gói đã được xử lý XONG ở đây. Gói chỉ được "nghe lỏm" để nhặt tên
     *     luôn trả {@code false}: chúng thuộc về coordinator khác.
     */
    boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        learnNames(opcode, payload);
        return dispatchDuelEvent(opcode, payload) || dispatchCommandResponse(opcode, payload);
    }

    /**
     * Bốn event server ĐẨY xuống — nguồn DUY NHẤT làm đổi trạng thái trận đấu, xem javadoc lớp.
     */
    private boolean dispatchDuelEvent(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_DUEL_CHALLENGE_RECEIVED_EVENT_VALUE) {
            onChallengeReceived(DuelChallengeReceivedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_DUEL_CHALLENGE_DECLINED_EVENT_VALUE) {
            onChallengeDeclined(DuelChallengeDeclinedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_DUEL_STARTED_EVENT_VALUE) {
            onDuelStarted(DuelStartedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_DUEL_RESULT_EVENT_VALUE) {
            onDuelResult(DuelResultEvent.parseFrom(payload));
            return true;
        }
        return false;
    }

    /** Bốn kết quả lệnh + một kết quả tra cứu (điểm xếp hạng). */
    private boolean dispatchCommandResponse(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_CHALLENGE_DUEL_RESPONSE_VALUE) {
            onChallengeResponse(ChallengeDuelResponse.parseFrom(payload));
        } else if (opcode == Opcode.OPCODE_ACCEPT_DUEL_RESPONSE_VALUE) {
            // Dù OK hay NOT_FOUND, lời thách đấu đó không còn chờ mình nữa. OK KHÔNG có nghĩa là
            // trận đã bắt đầu — DuelStartedEvent mới nói điều đó (javadoc lớp).
            controller.onChallengeAnswered();
            settle(DuelController.acceptMessageKey(
                    AcceptDuelResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_DECLINE_DUEL_RESPONSE_VALUE) {
            controller.onChallengeAnswered();
            settle(DuelController.declineMessageKey(
                    DeclineDuelResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_EXIT_DUEL_RESPONSE_VALUE) {
            // Bỏ cuộc thành công vẫn KHÔNG tự tắt biển "đang đấu": server còn phải gửi
            // DuelResultEvent (thua) — tắt ở đây là đoán trước lời server.
            settle(DuelController.exitMessageKey(
                    ExitDuelResponse.parseFrom(payload).getOutcome()));
        } else if (opcode == Opcode.OPCODE_GET_PVP_RATING_RESPONSE_VALUE) {
            controller.onRating(GetPvpRatingResponse.parseFrom(payload));
            refreshView();
        } else {
            return false;
        }
        return true;
    }

    /**
     * Nhặt các cặp (id, tên) đi ngang qua — xem javadoc {@link DuelController} về khoảng trống #1.
     *
     * <p>Bốn nguồn này là TẤT CẢ những gì giao thức cho client biết về tên của người khác. Không
     * chiếm gói: hàm này không trả gì và {@link #dispatch} vẫn để chúng chảy tiếp tới
     * {@code ChatCoordinator}/{@code PartyCoordinator}/{@code FriendCoordinator}/
     * {@code GuildCoordinator}. Phân tích lại payload lần thứ hai là giá phải trả, và nó rẻ: bốn
     * gói này tới theo nhịp người chơi thao tác, không phải mỗi khung hình (Điều 31).
     */
    private void learnNames(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_CHAT_MESSAGE_EVENT_VALUE) {
            ChatMessageEvent event = ChatMessageEvent.parseFrom(payload);
            controller.rememberName(event.getSenderId(), event.getSenderName());
        } else if (opcode == Opcode.OPCODE_LIST_FRIENDS_RESPONSE_VALUE) {
            for (FriendView view : ListFriendsResponse.parseFrom(payload).getFriendsList()) {
                controller.rememberName(view.getCharacterId(), view.getName());
            }
        } else if (opcode == Opcode.OPCODE_LIST_PARTY_MEMBERS_RESPONSE_VALUE) {
            for (PartyMemberView view :
                    ListPartyMembersResponse.parseFrom(payload).getMembersList()) {
                controller.rememberName(view.getCharacterId(), view.getName());
            }
        } else if (opcode == Opcode.OPCODE_LIST_GUILD_MEMBERS_RESPONSE_VALUE) {
            for (GuildMemberView view :
                    ListGuildMembersResponse.parseFrom(payload).getMembersList()) {
                controller.rememberName(view.getCharacterId(), view.getName());
            }
        }
    }

    /**
     * Kết quả lời thách đấu mình vừa gửi. Chỉ {@code OK} mới ghi lại {@code challenge_id} —
     * arena.proto nói rõ field đó "chỉ có ý nghĩa khi outcome = OK", các nhánh khác nó là số 0.
     */
    private void onChallengeResponse(ChallengeDuelResponse response) {
        if (response.getOutcome() == ChallengeDuelOutcome.CHALLENGE_DUEL_OUTCOME_OK) {
            controller.onChallengeSent(response.getChallengeId());
        }
        settle(DuelController.challengeMessageKey(response.getOutcome()));
    }

    /**
     * Có người thách đấu mình — mở cửa sổ để trả lời, nếu được phép.
     *
     * <p>Nói ra bằng CẢ HAI kênh (chữ nổi + cửa sổ): cửa sổ có thể không mở được vì người chơi đang
     * gõ ở một ô nhập khác (xem {@link DuelPanel#openIfAllowed}), và một lời thách đấu tới trong im
     * lặng là một lời thách đấu chết.
     */
    private void onChallengeReceived(DuelChallengeReceivedEvent event) {
        DuelController.Player challenger = controller.onChallengeReceived(event);
        feedback.showSelfText(format("duel.label.incoming", challenger.displayName()), FloatingCombatText.LEVEL_UP);
        panel.openIfAllowed();
        refreshView();
    }

    private void onChallengeDeclined(DuelChallengeDeclinedEvent event) {
        if (!controller.onChallengeDeclined(event.getChallengeId())) {
            return; // gói lạc, không phải lời mình đang treo — xem DuelController#onChallengeDeclined.
        }
        notice("duel.notice.declined");
        refreshView();
    }

    private void onDuelStarted(DuelStartedEvent event) {
        DuelController.Player opponent = controller.onDuelStarted(event);
        feedback.showSelfText(format("duel.notice.started", opponent.displayName()), FloatingCombatText.LEVEL_UP);
        refreshView();
    }

    /**
     * Trận đấu kết thúc. Hỏi lại điểm xếp hạng NGAY sau đó dù {@code DuelResultEvent} đã mang điểm
     * mới: gói đó không mang số trận thắng/thua, và đó là hai con số còn lại của cửa sổ này.
     */
    private void onDuelResult(DuelResultEvent event) {
        Optional<DuelController.Result> result = controller.onDuelResult(event);
        result.ifPresentOrElse(this::announceResult, () -> notice("duel.notice.finished"));
        refreshView();
        refreshRating();
    }

    private void announceResult(DuelController.Result result) {
        String message = format(result.won() ? "duel.notice.won" : "duel.notice.lost", result.newRating());
        panel.setStatus(message);
        feedback.showSelfText(message, result.won() ? FloatingCombatText.LEVEL_UP : FloatingCombatText.DAMAGE_TAKEN);
    }

    private void challengeCandidate() {
        Optional<DuelController.Player> candidate = controller.candidate();
        if (candidate.isEmpty()) {
            notice("duel.error.no_candidate");
            return;
        }
        // Tên THẬT (chưa sanitize) — xem javadoc DuelController.Player về lý do phải giữ cả hai.
        sender.challengeDuel(candidate.get().name());
    }

    private void challengeTypedName(String typed) {
        DuelController.ComposeResult result = controller.composeTargetName(typed);
        switch (result) {
            case DuelController.ComposeResult.Send send -> sender.challengeDuel(send.targetName());
            case DuelController.ComposeResult.Reject reject -> panel.setStatus(text(reject.messageKey()));
            case DuelController.ComposeResult.Nothing ignored -> {
                // Ô nhập rỗng — không gửi, không mắng.
            }
        }
    }

    private void acceptIncoming() {
        controller.incomingChallenge().ifPresent(challenge -> sender.acceptDuel(challenge.challengeId()));
    }

    private void declineIncoming() {
        controller.incomingChallenge().ifPresent(challenge -> sender.declineDuel(challenge.challengeId()));
    }

    /** Hỏi lại điểm xếp hạng — đường DUY NHẤT biết điểm, xem {@code WorldCommandSender#requestPvpRating}. */
    private void refreshRating() {
        sender.requestPvpRating();
    }

    /**
     * Nói ra kết quả một lệnh.
     *
     * @param messageKey rỗng khi client không nhận ra outcome (server MỚI HƠN client) — nói thẳng ra
     *     là không hiểu thay vì im lặng hoặc đoán bừa.
     */
    private void settle(Optional<String> messageKey) {
        notice(messageKey.orElse("duel.error.unknown"));
        refreshView();
    }

    /** Một dòng thông báo hiện ở CẢ cửa sổ lẫn chữ nổi — cửa sổ có thể đang đóng. */
    private void notice(String messageKey) {
        String message = text(messageKey);
        panel.setStatus(message);
        feedback.showSelfText(message, FloatingCombatText.MISS);
    }

    /** Đẩy toàn bộ trạng thái hiện có xuống cửa sổ + HUD. Gọi sau MỖI thay đổi, không phải mỗi khung. */
    private void refreshView() {
        controller
                .rating()
                .ifPresentOrElse(
                        rating -> panel.setRating(
                                format3("duel.rating.line", rating.rating(), rating.wins(), rating.losses())),
                        () -> panel.setRating(text("duel.rating.unknown")));
        controller
                .candidate()
                .ifPresentOrElse(
                        candidate -> panel.showCandidate(format("duel.label.candidate", candidate.displayName())),
                        panel::hideCandidate);
        controller
                .incomingChallenge()
                .ifPresentOrElse(
                        challenge -> panel.showIncomingChallenge(format(
                                "duel.label.incoming", challenge.challenger().displayName())),
                        panel::hideIncomingChallenge);
        controller.opponent().ifPresentOrElse(this::showDuel, this::hideDuel);
    }

    private void showDuel(DuelController.Player opponent) {
        panel.showDuel(format("duel.label.in_duel", opponent.displayName()));
        hud.setDuelStatus(format("duel.hud.in_duel", opponent.displayName()));
    }

    private void hideDuel() {
        panel.hideDuel();
        hud.clearDuelStatus();
    }

    /** Tra bản dịch, thiếu thì hiện thẳng khoá — cùng lý do {@code DialoguePanel#text}. */
    private String text(String key) {
        try {
            return i18n.get(key);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    private String format(String key, Object argument) {
        try {
            return i18n.format(key, argument);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    private String format3(String key, Object first, Object second, Object third) {
        try {
            return i18n.format(key, first, second, third);
        } catch (java.util.MissingResourceException e) {
            return key;
        }
    }

    /** Rời màn hình/mất kết nối — trạng thái đấu của một phiên chơi không sống sót sang phiên sau. */
    void reset() {
        controller.clear();
        panel.close();
        panel.setStatus(text("duel.hint.open"));
        hud.clearDuelStatus();
        refreshView();
    }
}
