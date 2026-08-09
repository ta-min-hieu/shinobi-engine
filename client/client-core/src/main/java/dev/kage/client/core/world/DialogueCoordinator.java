package dev.kage.client.core.world;

import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.DialoguePanel;
import dev.kage.protocol.DialogueNodeResponse;
import dev.kage.protocol.Opcode;

/**
 * Nối {@link DialogueController} (trạng thái), {@link DialoguePanel} (hiển thị) và
 * {@link WorldCommandSender} (mạng) thành một luồng hội thoại chạy được (2026-08-09).
 *
 * <p>Tách khỏi {@link WorldScreen} vì lớp đó đang ở ĐÚNG trần {@code CyclomaticComplexity} của PMD,
 * và vì luồng này có một đặc điểm đáng gom về một chỗ: <b>lệnh chọn một lựa chọn có tới BỐN loại gói
 * trả lời khác nhau</b>, ba trong số đó không phải packet hội thoại
 * ({@code GatewayChannelHandler.handleSelectDialogueOptionBlocking}):
 *
 * <ul>
 *   <li>{@code DialogueNodeResponse} — đi tiếp sang node khác.
 *   <li>{@code DialogueEndedResponse} — {@code action="end"}.
 *   <li>{@code AcceptQuestResponse} — {@code action="accept_quest:<code>"}, server gọi thẳng
 *       {@code QuestFacade} rồi trả gói của feature-quest.
 *   <li>{@code ListShopItemsResponse} — {@code action="open_shop"}, tái dùng gói của feature-shop.
 * </ul>
 *
 * <p>Ba loại sau đều <b>kết thúc</b> cuộc hội thoại: server không giữ phiên nào
 * ({@code GetDialogueNodeUseCase} tra lại cây script mỗi lần gọi), nên không có "quay lại node cũ"
 * để mà chờ. Client vì vậy phải tự đóng panel và nói ra chuyện gì vừa xảy ra — im lặng đóng khung
 * hội thoại là đúng thứ đã làm chủ dự án tưởng combat hỏng hôm 2026-08-09.
 */
final class DialogueCoordinator {

    private final DialogueController controller = new DialogueController();
    private final DialoguePanel panel;
    private final WorldCommandSender sender;
    private final CombatFeedbackPresenter feedback;

    private final QuestCoordinator quests;

    /**
     * @param feedback hiện thông báo nổi khi hội thoại kết thúc bằng một nhánh KHÔNG phải node (mở
     *     cửa hàng) — cùng kênh với "Chưa chọn mục tiêu"/"Ngoài tầm đánh", nên người chơi đọc mọi
     *     phản hồi tức thời ở cùng một chỗ.
     * @param quests nhận {@code action} của lựa chọn vừa bấm, để lấy mã nhiệm vụ.
     */
    DialogueCoordinator(
            DialoguePanel panel, WorldCommandSender sender, CombatFeedbackPresenter feedback, QuestCoordinator quests) {
        this.panel = panel;
        this.sender = sender;
        this.feedback = feedback;
        this.quests = quests;
        panel.setOnOptionSelected(this::onOptionSelected);
        // ESC chỉ dọn UI được; trạng thái hội thoại nằm ở controller nên phải dọn theo, nếu không
        // beginTalk lần sau sẽ bị chính cuộc cũ chặn lại (isOpen vẫn true) và NPC hoá ra "câm".
        panel.setOnClosed(controller::close);
    }

    /** Người chơi click trúng một NPC — mở hội thoại nếu chưa có cuộc nào đang mở. */
    void talkTo(String npcCode) {
        if (controller.beginTalk(npcCode)) {
            sender.talkToNpc(npcCode);
        }
    }

    boolean isOpen() {
        return controller.isOpen();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng hội thoại.
     *
     * @return {@code true} nếu gói đã được xử lý ở đây — người gọi dừng, không định tuyến tiếp.
     */
    boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_DIALOGUE_NODE_RESPONSE_VALUE) {
            onNode(DialogueNodeResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_DIALOGUE_ENDED_RESPONSE_VALUE) {
            closePanel();
            return true;
        }
        // Hai opcode dưới đây KHÔNG thuộc dialogue.proto và vẫn có nghĩa riêng ngoài hội thoại (nút
        // cửa hàng/nhật ký nhiệm vụ sau này) — chỉ nuốt khi ĐANG nói chuyện, ngược lại trả false để
        // người gọi định tuyến bình thường.
        if (!controller.isOpen()) {
            return false;
        }
        if (opcode == Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE) {
            // CHỈ đóng khung thoại. Việc cập nhật nhiệm vụ là của QuestCoordinator, và nó đã nhận
            // cùng gói này TRƯỚC khi tới đây (xem WorldScreen.dispatch) — trả true ở đây không giấu
            // mất gói của ai cả.
            closePanel();
            return true;
        }
        if (opcode == Opcode.OPCODE_LIST_SHOP_ITEMS_RESPONSE_VALUE) {
            // Client chưa có giao diện cửa hàng. Đóng hội thoại + nói thẳng là chưa mở được, thay vì
            // để người chơi bấm "Cho tôi xem hàng" rồi không có gì xảy ra cả.
            closePanel();
            feedback.showSelfNotice("dialogue.notice.shop_unavailable", FloatingCombatText.MISS);
            return true;
        }
        return false;
    }

    private void onNode(DialogueNodeResponse response) {
        controller.onNode(response);
        panel.show(controller.textKey(), controller.optionLabelKeys());
    }

    private void onOptionSelected(int optionIndex) {
        if (!controller.isSelectableOption(optionIndex)) {
            return;
        }
        // Mã nhiệm vụ chỉ có trong `action` của chính lựa chọn này — đọc TRƯỚC khi gửi, vì gói trả
        // lời (AcceptQuestResponse) không mang mã, xem QuestCoordinator.
        quests.onDialogueOptionSelected(controller.optionAction(optionIndex));
        sender.selectDialogueOption(controller.npcCode(), controller.currentNodeId(), optionIndex);
    }

    private void closePanel() {
        controller.close();
        panel.hide();
    }

    /** Đóng hội thoại khi rời màn hình/mất kết nối — panel không được sống sót qua một phiên chơi. */
    void reset() {
        closePanel();
    }
}
