package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.QuestLogPanel;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;

/**
 * Vòng đời nhiệm vụ ở phía client (2026-08-09, mở rộng 2026-08-29 theo ADR-035): nhận → server báo
 * hoàn thành → lĩnh thưởng, cộng thêm {@link QuestLogPanel} (phím J) hiện TOÀN BỘ nhật ký chứ không
 * chỉ nhiệm vụ đang theo dõi.
 *
 * <p>Bốn opcode gốc (606-609) đều <b>chưa từng được client dùng</b> trước 2026-08-09, dù
 * feature-quest phía server đã xong từ Phase 6 — nhận nhiệm vụ là thứ chỉ tồn tại trong test. Hội
 * thoại NPC vừa mở được cánh cửa đầu tiên ({@link DialogueCoordinator}); lớp này đóng nốt vòng.
 *
 * <p><b>2026-08-29 — vá bug thật "nhận nhiệm vụ → thoát game → đăng nhập lại → mất thưởng vĩnh
 * viễn"</b>: hai opcode mới (610/611, {@link Opcode#OPCODE_LIST_QUESTS_REQUEST_VALUE}/
 * {@link Opcode#OPCODE_LIST_QUESTS_RESPONSE_VALUE}) cho phép hỏi lại TOÀN BỘ nhật ký bất cứ lúc
 * nào, không phụ thuộc {@code pendingAcceptQuestCode} (biến chỉ sống trong phiên chơi hiện tại — xem
 * javadoc cũ của {@link QuestTracker}, còn trong lịch sử git). Gửi lại lúc VÀO WORLD (constructor)
 * và lúc MỞ {@link QuestLogPanel} — xem {@link #onLogOpened}.
 *
 * <p>Server <b>không</b> phát tiến độ từng bước (quest.proto: "chỉ phát khi quest VỪA chuyển
 * COMPLETED... tránh quá tải World tick"), nên client chỉ có bốn mốc {@link QuestStatus} để hiển
 * thị. Đó là lý do cả HUD góc màn hình lẫn nhật ký chỉ hiện trạng thái chứ không hiện "đã giết 2/3"
 * — con số đó nằm trong {@code progress_json} mà giao thức cố ý không gửi.
 */
final class QuestCoordinator {

    /**
     * Tiền tố của {@code action} mở đầu một nhiệm vụ, khớp {@code town_elder.js} và
     * {@code DialogueFacade}. Phần đứng sau là {@code quest_code}.
     */
    private static final String ACCEPT_QUEST_ACTION_PREFIX = "accept_quest:";

    private final QuestTracker tracker = new QuestTracker();
    private final CombatHud hud;
    private final QuestLogPanel panel;
    private final WorldCommandSender sender;
    private final CombatFeedbackPresenter feedback;
    private final I18NBundle i18n = KageI18n.load();

    /** Đệm dựng sẵn cho {@link QuestLogPanel#setQuests} — chỉ đổi khi server vừa trả lời. */
    private final List<QuestLogPanel.QuestRow> questRows = new ArrayList<>();

    /**
     * Mã nhiệm vụ đã bấm nhưng server CHƯA xác nhận. Tách khỏi {@link QuestTracker} có chủ đích: bấm
     * nút chưa phải là đã nhận được — server còn có thể trả {@code QUEST_NOT_FOUND} hoặc
     * {@code ALREADY_ACCEPTED} (Điều 15).
     */
    private String pendingAcceptQuestCode = "";

    /**
     * Cổng phím J ({@link QuestLogPanel#setCanOpen}) được nối ở {@code WorldScreen#wireHud} chứ
     * không qua tham số constructor ở đây — cùng quy ước {@code partyPanel}/{@code chatPanel}/
     * {@code duelPanel}, và cần thiết để giữ constructor này ở 4 tham số (CODING-STANDARDS
     * ParameterNumber max=4): lớp đã cần cả {@link CombatHud} lẫn {@link QuestLogPanel}, khác
     * {@link MailCoordinator} (chỉ có một panel).
     */
    QuestCoordinator(CombatHud hud, QuestLogPanel panel, WorldCommandSender sender, CombatFeedbackPresenter feedback) {
        this.hud = hud;
        this.panel = panel;
        this.sender = sender;
        this.feedback = feedback;
        hud.setOnClaimQuestClicked(this::claimReward);
        panel.setOnRefresh(this::onLogOpened);
        panel.setOnClaim(this::claimFromLog);
        panel.setStatus(text("questlog.hint.close"));
        // ADR-035 — hỏi ngay lúc vào world, không đợi người chơi mở J: nhiệm vụ hoàn thành TRƯỚC lúc
        // relog phải hiện ra với nút Lĩnh thưởng ngay cả khi họ chưa kịp mở nhật ký ra xem.
        sender.listQuests();
    }

    /**
     * Người chơi vừa chọn một lựa chọn hội thoại — nếu đó là lựa chọn nhận nhiệm vụ thì nhớ mã lại.
     *
     * <p>Đây là nguồn DUY NHẤT của {@code quest_code} phía client TRONG PHIÊN CHƠI HIỆN TẠI:
     * {@code AcceptQuestResponse} chỉ mang {@code outcome}, không mang mã. Nó KHÔNG còn là nguồn
     * duy nhất qua relog — xem javadoc lớp về {@code ListQuestsResponse}.
     */
    void onDialogueOptionSelected(String action) {
        if (action.startsWith(ACCEPT_QUEST_ACTION_PREFIX)) {
            pendingAcceptQuestCode = action.substring(ACCEPT_QUEST_ACTION_PREFIX.length());
        }
    }

    /**
     * Cửa sổ nhật ký đang mở hay không — {@code WorldScreen} phải chặn MỌI phím gameplay khi câu
     * này đúng, xem {@link QuestLogPanel#isOpen()}.
     */
    boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng cửa sổ nhật ký (mở hội thoại NPC, rời màn hình) — nhật ký đã tải vẫn giữ nguyên. */
    void close() {
        panel.close();
    }

    /**
     * Định tuyến một gói có thể thuộc luồng nhiệm vụ.
     *
     * @return {@code true} nếu gói thuộc luồng nhiệm vụ và đã xử lý xong.
     */
    boolean dispatch(int opcode, byte[] payload) throws InvalidProtocolBufferException {
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
        if (opcode == Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE) {
            onQuestsListed(ListQuestsResponse.parseFrom(payload));
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
            refreshLogView();
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
        refreshLogView();
        if (tracker.isClaimable()) {
            feedback.showSelfNotice("quest.notice.completed", FloatingCombatText.LEVEL_UP);
        }
    }

    /**
     * Server vừa trả trọn nhật ký (opcode 611) — nguồn phục hồi CHÍNH sau relog, xem javadoc lớp và
     * {@link QuestTracker#onQuestsListed}.
     */
    private void onQuestsListed(ListQuestsResponse response) {
        tracker.onQuestsListed(response);
        syncHud();
        refreshLogView();
    }

    /** Bấm nút Nhận thưởng trên HUD góc màn hình — luôn là nhiệm vụ ĐANG THEO DÕI trong phiên chơi. */
    private void claimReward() {
        if (!tracker.isClaimable()) {
            return; // Nút lẽ ra đã tắt — chặn thêm ở đây để không gửi gói chắc chắn bị từ chối.
        }
        sender.claimQuestReward(tracker.questCode(), tracker.claimKey());
    }

    /**
     * Bấm nút Nhận thưởng trên MỘT DÒNG trong {@link QuestLogPanel} — mã lấy từ chính dòng đó, KHÔNG
     * phải từ "nhiệm vụ đang theo dõi". Đây là đường sống sót qua relog: {@code questCode} đến từ
     * {@code ListQuestsResponse} (qua {@link QuestTracker#questLog()}), không từ
     * {@link #pendingAcceptQuestCode} hay bất cứ biến nào chỉ sống trong phiên chơi hiện tại.
     */
    private void claimFromLog(String questCode) {
        if (!tracker.isClaimable(questCode) || tracker.isClaimPending(questCode)) {
            return; // Nút lẽ ra đã tắt — chặn thêm ở đây để không gửi gói chắc chắn bị từ chối.
        }
        sender.claimQuestReward(questCode, tracker.claimKey(questCode));
    }

    /**
     * Kết quả một lệnh nhận thưởng — DÙNG CHUNG cho cả hai đường gửi ở trên
     * ({@link #claimReward()}/{@link #claimFromLog(String)}). {@code ClaimQuestRewardResponse}
     * KHÔNG mang lại {@code quest_code} (quest.proto), nên không có cách nào phân biệt "kết quả của
     * lệnh nào" ngoài việc {@link QuestTracker} tự nhớ mã của lệnh đang treo — xem javadoc
     * {@code QuestTracker.pendingClaimQuestCode}.
     */
    private void onClaimResponse(ClaimQuestRewardResponse response) {
        ClaimQuestRewardOutcome outcome = response.getOutcome();
        // DUPLICATE_REQUEST cũng tính là XONG (ADR-025): nghĩa là chính lệnh này đã được thực thi
        // trước đó rồi, phần thưởng đã về tay — coi nó là lỗi sẽ khiến người chơi bấm mãi.
        boolean claimed = outcome == ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK
                || outcome == ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_DUPLICATE_REQUEST;
        tracker.onClaimSettled(claimed);
        syncHud();
        // Nói kết quả CỤ THỂ trên dòng trạng thái của nhật ký (mọi outcome đều có khoá riêng, xem
        // QuestTracker.claimOutcomeMessageKey) — thay vì chỉ hai câu chung chung "xong"/"hỏng", một
        // outcome server MỚI THÊM mà client chưa biết vẫn nói ra được là "không rõ", không im lặng.
        panel.setStatus(text(QuestTracker.claimOutcomeMessageKey(outcome).orElse("quest.claim_outcome.unknown")));
        // Hỏi lại TOÀN BỘ nhật ký sau lệnh — cùng luật "hỏi lại sau MỌI lệnh đổi trạng thái" của
        // MailCoordinator/GuildCoordinator: suy nhật ký từ mỗi response riêng lẻ dễ trôi khỏi sự
        // thật hơn là hỏi thẳng server, nhất là khi client có thể đang giữ nhiều nhiệm vụ mà nó
        // chưa từng biết mã (server không giới hạn một nhiệm vụ một lúc, chỉ HUD góc màn hình làm
        // vậy để đơn giản hoá hiển thị).
        sender.listQuests();
        if (!claimed) {
            feedback.showSelfNotice("quest.notice.claim_failed", FloatingCombatText.MISS);
            return;
        }
        showRewards(response);
    }

    /**
     * Nói ra phần thưởng thật. EXP đã có vạch riêng trên HUD (server gửi
     * {@code ExperienceChangedEvent} sau đó), nhưng vật phẩm thì client CHƯA có túi đồ nào để hiện —
     * dòng chữ nổi này là bằng chứng duy nhất người chơi nhận được rằng phần thưởng đã về.
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

    /** Người chơi vừa mở {@link QuestLogPanel} (hoặc bấm Làm mới) — hỏi lại server ngay. */
    private void onLogOpened() {
        sender.listQuests();
    }

    /** Đẩy toàn bộ nhật ký hiện có xuống panel. Gọi sau MỖI response tra cứu, không phải mỗi khung. */
    private void refreshLogView() {
        List<QuestTracker.LoggedQuest> all = tracker.questLog();
        int shown = Math.min(all.size(), QuestTracker.MAX_VISIBLE_ROWS);
        questRows.clear();
        for (int index = 0; index < shown; index++) {
            QuestTracker.LoggedQuest entry = all.get(index);
            questRows.add(new QuestLogPanel.QuestRow(entry.questCode(), rowText(entry), rowClaimable(entry)));
        }
        panel.setQuests(questRows, listNotice(all.size(), all.size() - shown));
    }

    /**
     * Có bày nút Lĩnh thưởng cho dòng này không — hoàn thành, và KHÔNG có lệnh nào đang treo cho
     * đúng mã này (nút lẽ ra đã disable trong lúc chờ, cùng lý do {@code claimQuestButton} ở HUD).
     */
    private boolean rowClaimable(QuestTracker.LoggedQuest entry) {
        return tracker.isClaimable(entry.questCode()) && !tracker.isClaimPending(entry.questCode());
    }

    /** Một dòng nhật ký: {@code Tên nhiệm vụ — Trạng thái}. */
    private String rowText(QuestTracker.LoggedQuest entry) {
        return translateOrKey("quest.name." + entry.questCode()) + " — " + statusText(entry.status());
    }

    private String statusText(QuestStatus status) {
        return text(QuestTracker.statusMessageKey(status).orElse("quest.status.unknown"));
    }

    /**
     * Câu nói thêm dưới danh sách: "chưa nhận nhiệm vụ nào", hoặc "còn N nhiệm vụ nữa không hiện",
     * hoặc không nói gì — cùng lý do {@code MailCoordinator#listNotice}.
     */
    private String listNotice(int total, int hidden) {
        if (total == 0) {
            return text("questlog.empty");
        }
        return hidden <= 0 ? "" : format("questlog.list.truncated", hidden);
    }

    /**
     * Tên nhiệm vụ tra theo {@code quest_code} — thiếu bản dịch thì hiện thẳng mã, cùng lý do
     * {@code CombatHud#translateOrKey} (nội dung nhiệm vụ nằm trong script GraalJS phía server, bản
     * dịch nằm ở client, hai nhịp cập nhật khác nhau).
     */
    private String translateOrKey(String key) {
        return text(key);
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

    /**
     * Rời màn hình/mất kết nối — nhật ký của một phiên chơi không được sống sót sang phiên sau, cùng
     * quy ước {@link MailCoordinator#reset()}. Vào world mới sẽ tự hỏi lại (constructor).
     */
    void reset() {
        tracker.reset();
        panel.close();
        panel.setStatus(text("questlog.hint.close"));
        refreshLogView();
        syncHud();
        pendingAcceptQuestCode = "";
    }
}
