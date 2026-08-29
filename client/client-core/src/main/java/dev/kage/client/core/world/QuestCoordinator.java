package dev.kage.client.core.world;

import com.badlogic.gdx.graphics.Color;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.wire.PacketCodec;

/**
 * Vòng đời một nhiệm vụ ở phía client (2026-08-09): nhận → server báo hoàn thành → lĩnh thưởng.
 *
 * <p>Cả bốn opcode quest (606-609) đều <b>chưa từng được client dùng</b> trước hôm nay, dù
 * feature-quest phía server đã xong từ Phase 6 — nhận nhiệm vụ là thứ chỉ tồn tại trong test. Hội
 * thoại NPC vừa mở được cánh cửa đầu tiên ({@link DialogueCoordinator}); lớp này đóng nốt vòng.
 *
 * <p>Server <b>không</b> phát tiến độ từng bước (quest.proto: "chỉ phát khi quest VỪA chuyển
 * COMPLETED... tránh quá tải World tick"), nên client chỉ có hai mốc để hiển thị: đang làm, và xong.
 * Đó là lý do HUD chỉ hiện trạng thái chứ không hiện "đã giết 2/3" — con số đó nằm trong
 * {@code progress_json} mà giao thức cố ý không gửi.
 */
final class QuestCoordinator implements WindowFlow {

    /**
     * Tiền tố của {@code action} mở đầu một nhiệm vụ, khớp {@code town_elder.js} và
     * {@code DialogueFacade}. Phần đứng sau là {@code quest_code}.
     */
    private static final String ACCEPT_QUEST_ACTION_PREFIX = "accept_quest:";

    private final QuestTracker tracker = new QuestTracker();
    private final CombatHud hud;
    private final WorldCommandSender sender;
    private final CombatFeedbackPresenter feedback;

    /**
     * Mã nhiệm vụ đã bấm nhưng server CHƯA xác nhận. Tách khỏi {@link QuestTracker} có chủ đích: bấm
     * nút chưa phải là đã nhận được — server còn có thể trả {@code QUEST_NOT_FOUND} hoặc
     * {@code ALREADY_ACCEPTED} (Điều 15).
     */
    private String pendingAcceptQuestCode = "";

    QuestCoordinator(CombatHud hud, WorldCommandSender sender, CombatFeedbackPresenter feedback) {
        this.hud = hud;
        this.sender = sender;
        this.feedback = feedback;
        hud.setOnClaimQuestClicked(this::claimReward);
    }

    /**
     * Người chơi vừa chọn một lựa chọn hội thoại — nếu đó là lựa chọn nhận nhiệm vụ thì nhớ mã lại.
     *
     * <p>Đây là nguồn DUY NHẤT của {@code quest_code} phía client: {@code AcceptQuestResponse} chỉ
     * mang {@code outcome}, không mang mã. Xem {@link QuestTracker} về hệ quả của việc đó.
     */
    void onDialogueOptionSelected(String action) {
        if (action.startsWith(ACCEPT_QUEST_ACTION_PREFIX)) {
            pendingAcceptQuestCode = action.substring(ACCEPT_QUEST_ACTION_PREFIX.length());
        }
    }

    /**
     * Định tuyến một gói có thể thuộc luồng nhiệm vụ.
     *
     * @return {@code true} nếu gói thuộc luồng nhiệm vụ và đã xử lý xong.
     */
    @Override
    public boolean dispatch(PacketCodec.Frame frame) throws InvalidProtocolBufferException {
        return dispatch(frame.header().opcode(), frame.payload());
    }

    private boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
        if (opcode == Opcode.OPCODE_ACCEPT_QUEST_RESPONSE_VALUE) {
            onAcceptResponse(AcceptQuestResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_QUEST_STATUS_CHANGED_EVENT_VALUE) {
            onStatusChanged(QuestStatusChangedEvent.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_CLAIM_QUEST_REWARD_RESPONSE_VALUE) {
            onClaimResponse(ClaimQuestRewardResponse.parseFrom(payload));
            return true;
        }
        return false;
    }

    /**
     * ⚠️ Gói này KHÔNG bị {@link DialogueCoordinator} nuốt mất dù nó cũng nhận opcode đó: bên kia
     * dùng nó để ĐÓNG khung thoại (hội thoại rẽ sang luồng khác là kết thúc), bên này để cập nhật
     * nhiệm vụ. Hai việc khác nhau trên cùng một gói, nên {@code WorldScreen} phải gọi CẢ HAI chứ
     * không dừng ở cái đầu tiên trả {@code true} — xem chỗ gọi.
     */
    private void onAcceptResponse(AcceptQuestResponse response) {
        if (response.getOutcome() == AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_OK && !pendingAcceptQuestCode.isEmpty()) {
            tracker.onAccepted(pendingAcceptQuestCode);
            syncHud();
        }
        // Nói ra kết quả kể cả khi hỏng: "đã nhận rồi" và "không nhận được" nhìn giống hệt nhau nếu
        // im lặng — khung thoại đóng lại, HUD không đổi gì, và người chơi không biết vì sao.
        feedback.showSelfNotice(acceptNoticeKey(response.getOutcome()), acceptNoticeColor(response.getOutcome()));
        // Thất bại thì bỏ mã đang chờ đi: giữ lại chỉ tạo ra khả năng gán nhầm nó cho một lần nhận
        // nhiệm vụ KHÁC thành công sau này.
        pendingAcceptQuestCode = "";
    }

    private static String acceptNoticeKey(AcceptQuestOutcome outcome) {
        return switch (outcome) {
            case ACCEPT_QUEST_OUTCOME_OK -> "quest.notice.accepted";
            case ACCEPT_QUEST_OUTCOME_ALREADY_ACCEPTED -> "quest.notice.already_accepted";
            default -> "quest.notice.accept_failed";
        };
    }

    private static Color acceptNoticeColor(AcceptQuestOutcome outcome) {
        return outcome == AcceptQuestOutcome.ACCEPT_QUEST_OUTCOME_OK
                ? FloatingCombatText.LEVEL_UP
                : FloatingCombatText.MISS;
    }

    private void onStatusChanged(QuestStatusChangedEvent event) {
        tracker.onStatusChanged(event.getQuestCode(), event.getStatus());
        syncHud();
        if (tracker.isClaimable()) {
            feedback.showSelfNotice("quest.notice.completed", FloatingCombatText.LEVEL_UP);
        }
    }

    private void claimReward() {
        if (!tracker.isClaimable()) {
            return; // Nút lẽ ra đã tắt — chặn thêm ở đây để không gửi gói chắc chắn bị từ chối.
        }
        sender.claimQuestReward(tracker.questCode(), tracker.claimKey());
    }

    private void onClaimResponse(ClaimQuestRewardResponse response) {
        ClaimQuestRewardOutcome outcome = response.getOutcome();
        // DUPLICATE_REQUEST cũng tính là XONG (ADR-025): nghĩa là chính lệnh này đã được thực thi
        // trước đó rồi, phần thưởng đã về tay — coi nó là lỗi sẽ khiến người chơi bấm mãi.
        boolean claimed = outcome == ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK
                || outcome == ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_DUPLICATE_REQUEST;
        tracker.onClaimSettled(claimed);
        syncHud();
        if (!claimed) {
            feedback.showSelfNotice("quest.notice.claim_failed", FloatingCombatText.MISS);
            return;
        }
        showRewards(response);
    }

    /**
     * Nói ra phần thưởng thật. EXP đã có vạch riêng trên HUD (server gửi
     * {@code ExperienceChangedEvent} sau đó), nhưng vật phẩm thì client CHƯA có túi đồ nào để hiện —
     * dòng chữ nổi này là bằng chứng duy nhất người chơi nhận được rằng 5 cây kunai đã về.
     */
    private void showRewards(ClaimQuestRewardResponse response) {
        feedback.showSelfNotice("quest.notice.claimed", FloatingCombatText.LEVEL_UP);
        for (ItemRewardView item : response.getItemsGrantedList()) {
            feedback.showSelfText(
                    item.getItemTemplateCode() + " x" + item.getQuantity(), FloatingCombatText.DAMAGE_DEALT);
        }
    }

    private void syncHud() {
        if (tracker.isTracking()) {
            hud.setQuest(tracker.questCode(), tracker.isClaimable());
        } else {
            hud.clearQuest();
        }
    }
}
