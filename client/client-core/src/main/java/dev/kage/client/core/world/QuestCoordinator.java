package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.List;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.utils.I18NBundle;
import com.google.protobuf.InvalidProtocolBufferException;

import dev.kage.client.core.ui.hud.CombatHud;
import dev.kage.client.core.ui.hud.QuestLogPanel;
import dev.kage.client.core.ui.hud.ScrollingList;
import dev.kage.client.core.ui.theme.KageI18n;
import dev.kage.protocol.AbandonQuestOutcome;
import dev.kage.protocol.AbandonQuestResponse;
import dev.kage.protocol.AcceptQuestOutcome;
import dev.kage.protocol.AcceptQuestResponse;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ClaimQuestRewardResponse;
import dev.kage.protocol.EscortStateEvent;
import dev.kage.protocol.ItemRewardView;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.Opcode;
import dev.kage.protocol.QuestStatus;
import dev.kage.protocol.QuestStatusChangedEvent;
import dev.kage.protocol.wire.PacketCodec;

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
final class QuestCoordinator implements WindowFlow {

    /**
     * Tiền tố của {@code action} mở đầu một nhiệm vụ, khớp {@code town_elder.js} và
     * {@code DialogueFacade}. Phần đứng sau là {@code quest_code}.
     */
    private static final String ACCEPT_QUEST_ACTION_PREFIX = "accept_quest:";

    private final QuestTracker tracker = new QuestTracker();
    private final CombatHud hud;
    private final QuestLogPanel panel;

    /** ADR-221 — tự đi tới một NPC theo mã (tự tìm đường ADR-206); mặc định không làm gì tới khi được nối. */
    private java.util.function.Consumer<String> goTo = npcCode -> {};

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
        panel.setOnAbandon(sender::abandonQuest);
        panel.setOnGoTo(npcCode -> goTo.accept(npcCode)); // ADR-221 — nối thật ở useGoTo
        panel.setStatus(text("questlog.hint.open"));
    }

    /** ADR-221 — nối nút "Đi tới" vào tự tìm đường (một lần, lúc dựng màn hình). */
    void useGoTo(java.util.function.Consumer<String> walker) {
        this.goTo = walker;
    }

    /**
     * Hỏi trọn nhật ký nhiệm vụ. {@code WorldScreen} gọi ở {@code show()}, không phải ở constructor:
     * {@link #reset()} xoá sạch nhật ký mỗi lần rời màn hình, nên hỏi một lần lúc dựng là ra/vào lại
     * màn hình sẽ thấy nhật ký RỖNG mãi mãi (và mất luôn nút Lĩnh thưởng trên HUD) cho tới khi người
     * chơi tình cờ bấm J. Cùng chỗ, cùng lý do với {@link ItemCatalogCoordinator#request()}.
     *
     * <p>ADR-035 — hỏi ngay lúc vào world chứ không đợi mở J: nhiệm vụ hoàn thành TRƯỚC lúc relog
     * phải hiện ra với nút Lĩnh thưởng ngay cả khi người chơi chưa kịp mở nhật ký ra xem.
     */
    void requestLog() {
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
    @Override
    public boolean isOpen() {
        return panel.isOpen();
    }

    /** Đóng cửa sổ nhật ký (mở hội thoại NPC, rời màn hình) — nhật ký đã tải vẫn giữ nguyên. */
    @Override
    public void close() {
        panel.close();
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
        if (opcode == Opcode.OPCODE_LIST_QUESTS_RESPONSE_VALUE) {
            onQuestsListed(ListQuestsResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_ABANDON_QUEST_RESPONSE_VALUE) {
            onAbandonResponse(AbandonQuestResponse.parseFrom(payload));
            return true;
        }
        if (opcode == Opcode.OPCODE_ESCORT_STATE_EVENT_VALUE) {
            onEscortState(EscortStateEvent.parseFrom(payload));
            return true;
        }
        return false;
    }

    /**
     * ADR-114 — gói 625 cũng tới {@code DungeonCoordinator} (dải chữ của đoàn); ở đây chỉ lo phần nhiệm vụ: đoàn
     * vỡ/bỏ ⇒ server đã bỏ quest, gỡ khỏi nhật ký rồi hỏi lại — xem {@link QuestTracker#onEscortEnded}.
     */
    private void onEscortState(EscortStateEvent event) {
        if (tracker.onEscortEnded(event.getQuestCode(), event.getState())) {
            syncHud();
            refreshLogView();
            sender.listQuests();
        }
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
            // ADR-044 — nói ĐÚNG lý do. "Không nhận được nhiệm vụ" cho người chơi biết là hỏng
            // nhưng không biết phải làm gì; câu này chỉ thẳng sang việc còn dở.
            case ACCEPT_QUEST_OUTCOME_PREREQUISITE_NOT_MET -> "quest.notice.prerequisite_not_met";
            // Lỗi của MÁY CHỦ, không phải thứ người chơi làm sai. Câu chung "không nhận được nhiệm
            // vụ" sẽ khiến họ bấm lại mãi một việc không bao giờ chạy được.
            case ACCEPT_QUEST_OUTCOME_SCRIPT_ERROR -> "quest.notice.script_error";
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

    /**
     * Kết quả lệnh bỏ nhiệm vụ (ADR-054). Thành công thì gỡ ngay khỏi nhật ký + HUD góc màn hình rồi
     * vẫn hỏi lại server — cùng luật "hỏi lại sau MỌI lệnh đổi trạng thái" của {@link #onClaimResponse}.
     */
    private void onAbandonResponse(AbandonQuestResponse response) {
        boolean abandoned = response.getOutcome() == AbandonQuestOutcome.ABANDON_QUEST_OUTCOME_OK;
        if (abandoned) {
            tracker.onAbandoned(response.getQuestCode());
            syncHud();
            refreshLogView();
        }
        panel.setStatus(text(
                QuestTracker.abandonOutcomeMessageKey(response.getOutcome()).orElse("quest.abandon_outcome.unknown")));
        sender.listQuests();
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
        if (response.getGoldGranted() > 0L) {
            feedback.showSelfText(
                    format("quest.notice.gold_reward", response.getGoldGranted()), FloatingCombatText.DAMAGE_DEALT);
        }
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
        int shown = Math.min(all.size(), ScrollingList.MAX_BUILT_ROWS);
        questRows.clear();
        for (int index = 0; index < shown; index++) {
            QuestTracker.LoggedQuest entry = all.get(index);
            questRows.add(new QuestLogPanel.QuestRow(
                    entry.questCode(),
                    rowText(entry),
                    rowClaimable(entry),
                    entry.status() == QuestStatus.QUEST_STATUS_IN_PROGRESS,
                    entry.targetNpcCode()));
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
        // Đã lĩnh thưởng HẾT: trước đây câu này im lặng, nên người chơi cày xong mọi nhiệm vụ chỉ
        // thấy một danh sách toàn "đã nhận thưởng" và không có gì nói cho họ biết phải làm gì tiếp
        // — một bức tường vô hình. Ưu tiên trước câu "còn N dòng chưa hiện" vì khi mọi thứ đã xong
        // thì phần bị cắt bớt không còn là thông tin người chơi cần.
        if (tracker.everyQuestClaimed()) {
            return text("questlog.all_claimed");
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
        return KageI18n.textOrKey(i18n, key);
    }

    private String format(String key, Object argument) {
        return KageI18n.formatOrKey(i18n, key, argument);
    }

    /**
     * Rời màn hình/mất kết nối — nhật ký của một phiên chơi không được sống sót sang phiên sau, cùng
     * quy ước {@link MailCoordinator#reset()}. Vào world lần sau sẽ tự hỏi lại — xem
     * {@link #requestLog()} về lý do lời gọi đó nằm ở {@code show()} chứ không phải constructor.
     */
    @Override
    public void reset() {
        tracker.reset();
        panel.close();
        panel.setStatus(text("questlog.hint.open"));
        refreshLogView();
        syncHud();
        pendingAcceptQuestCode = "";
    }
}
