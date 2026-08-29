package dev.kage.client.core.world;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import dev.kage.client.core.ui.hud.QuestLogPanel;
import dev.kage.protocol.ClaimQuestRewardOutcome;
import dev.kage.protocol.ListQuestsResponse;
import dev.kage.protocol.QuestLogEntry;
import dev.kage.protocol.QuestStatus;

/**
 * Nhiệm vụ — mã, trạng thái, và khoá idempotency của lần nhận thưởng (2026-08-09). Thuần Java,
 * không GL, test được thẳng (UI.md §4).
 *
 * <p><b>2026-08-29 — ADR-035 (opcode 610/611) lấp đúng lỗ hổng mà javadoc bản trước của lớp này ghi
 * lại</b>: "quest_code không sống sót qua relog vì giao thức không có opcode nào hỏi lại được nhật
 * ký". Giờ có {@link #onQuestsListed}, nguồn của {@link #log} — bản sao đầy đủ nhật ký nhiệm vụ,
 * nạp lại được bất cứ lúc nào từ {@code ListQuestsResponse}, không phụ thuộc bộ nhớ phiên chơi. Đây
 * chính là gói vá bug thật "nhận nhiệm vụ → thoát game → đăng nhập lại → mất thưởng vĩnh viễn": sau
 * relog, {@link #questCode}/{@link #status} (bên dưới) trống trơn vì chúng chỉ sống trong phiên chơi
 * hiện tại, nhưng {@link #log} thì không — nó được nạp lại từ gói tin mới nhất, và
 * {@link #isClaimable(String)}/{@link #claimKey(String)} đọc từ đó, không đọc từ hai trường kia.
 *
 * <p>Lớp giữ ĐÚNG MỘT bộ trạng thái nhiệm vụ (không nhân đôi ra một class khác): hai cặp API dưới
 * đây là hai CÁCH ĐỌC khác nhau trên cùng một dữ liệu, không phải hai nguồn sự thật riêng biệt.
 *
 * <ul>
 *   <li><b>"Đang theo dõi" ({@link #questCode()}/{@link #status()}/{@link #isClaimable()}/
 *       {@link #claimKey()})</b> — nhiệm vụ HUD góc màn hình ({@code CombatHud}) đang hiện, nhận từ
 *       {@code AcceptQuestResponse} lúc vừa bấm nhận (mã đọc từ {@code action} hội thoại,
 *       {@code "accept_quest:<code>"} — xem {@link QuestCoordinator#onDialogueOptionSelected}) và
 *       cập nhật qua {@code QuestStatusChangedEvent}. Chỉ tồn tại trong phiên chơi hiện tại.
 *   <li><b>"Nhật ký" ({@link #questLog()}/{@link #isClaimable(String)}/{@link #claimKey(String)})</b>
 *       — TOÀN BỘ nhiệm vụ, nạp từ {@code ListQuestsResponse} (opcode 611), dùng cho
 *       {@link QuestLogPanel} (phím J). Sống sót qua relog vì nó không đến từ bộ nhớ phiên mà đến
 *       từ gói tin.
 * </ul>
 *
 * <p>Mọi thao tác đổi trạng thái nhiệm vụ (nhận, hoàn thành, lĩnh thưởng) đều cập nhật CẢ HAI cách
 * đọc cùng lúc — xem {@link #onAccepted}/{@link #onStatusChanged}/{@link #onClaimSettled} — nên
 * chúng không bao giờ lệch nhau.
 *
 * <p>⚠️ {@code progress_json} (tiến độ chi tiết kiểu "đã giết 2/3") KHÔNG xuất hiện ở đây và không
 * bao giờ xuất hiện: đó là chuỗi đục do script GraalJS tự quản (quest.proto), Java không đọc được.
 * Client chỉ có đúng bốn mốc {@link QuestStatus} để hiển thị.
 */
final class QuestTracker {

    /**
     * Số dòng nhật ký hiển thị cùng lúc ở {@link QuestLogPanel} — cùng trần
     * {@link GuildController#MAX_VISIBLE_ROWS}: {@code KageSkin} chưa có style {@code ScrollPane}
     * nào, nên danh sách dài sẽ đội khung cao vượt màn hình chứ không tự cuộn.
     */
    static final int MAX_VISIBLE_ROWS = 8;

    /** Một dòng nhật ký — bản sao BẤT BIẾN trả ra ngoài, xem {@link #questLog()}. */
    record LoggedQuest(String questCode, QuestStatus status) {}

    private String questCode = "";
    private QuestStatus status = QuestStatus.QUEST_STATUS_UNSPECIFIED;

    /**
     * Nhật ký ĐẦY ĐỦ — nguồn chân lý là {@code ListQuestsResponse} (opcode 611). {@link LinkedHashMap}
     * giữ đúng thứ tự server trả về gần nhất; quest.proto không cam kết thứ tự cụ thể, nhưng giữ ổn
     * định giữa hai lần vẽ vẫn tốt hơn xáo trộn ngẫu nhiên theo hash.
     */
    private final Map<String, QuestStatus> log = new LinkedHashMap<>();

    /**
     * Mã nhiệm vụ của lệnh nhận thưởng ĐANG CHỜ server trả lời — rỗng nếu không có lệnh nào đang
     * treo. MỘT lệnh tại một thời điểm là toàn bộ những gì giao thức cho phép phân biệt được:
     * {@code ClaimQuestRewardResponse} KHÔNG mang lại {@code quest_code} (quest.proto), nên hai lệnh
     * nhận thưởng chồng nhau (VD bấm nút HUD góc màn hình VÀ bấm nút một dòng khác trong nhật ký gần
     * như cùng lúc) sẽ không phân biệt được của cái nào — cùng khoảng trống {@code MailController}
     * đã ghi nhận cho {@code ClaimMailResponse} (mail.proto). Gộp cả hai đường nhận thưởng (HUD góc
     * màn hình lẫn nhật ký) về đúng một cặp trạng thái này là cách duy nhất không nói dối về giới
     * hạn đó — xem {@link #claimKey(String)}/{@link #onClaimSettled(boolean)}.
     */
    private String pendingClaimQuestCode = "";

    /** Khoá idempotency (ADR-025) của lệnh đang treo — rỗng khi không có lệnh nào đang treo. */
    private String pendingClaimKey = "";

    /** Nhiệm vụ vừa được nhận (server đã trả {@code ACCEPT_QUEST_OUTCOME_OK}). */
    void onAccepted(String acceptedQuestCode) {
        this.questCode = acceptedQuestCode;
        this.status = QuestStatus.QUEST_STATUS_IN_PROGRESS;
        log.put(acceptedQuestCode, QuestStatus.QUEST_STATUS_IN_PROGRESS);
    }

    /**
     * Server báo một nhiệm vụ đổi trạng thái. Cập nhật {@link #log} VÔ ĐIỀU KIỆN (server nói gì ghi
     * đúng vậy, Điều 15); cập nhật thêm ô "đang theo dõi" NẾU đúng mã đang hiện ở HUD góc màn hình —
     * một sự kiện của nhiệm vụ khác không được ghi đè cái đang hiển thị ở đó.
     */
    void onStatusChanged(String eventQuestCode, QuestStatus newStatus) {
        log.put(eventQuestCode, newStatus);
        if (isTracking() && questCode.equals(eventQuestCode)) {
            status = newStatus;
        }
    }

    /**
     * Server vừa trả trọn nhật ký (opcode 611, ADR-035) — THAY THẲNG {@link #log}, không trộn với
     * bản cũ: đây là nguồn chân lý MỚI NHẤT, giữ lại dữ liệu cũ chỉ tạo ra nhiệm vụ ma nếu server đã
     * quên nó (không nên xảy ra, nhưng thay toàn bộ vẫn an toàn hơn hợp nhất âm thầm).
     *
     * <p>Đây là lệnh gửi lúc vào world và lúc mở {@link QuestLogPanel} — chính là đường phục hồi sau
     * relog mà javadoc lớp mô tả.
     */
    void onQuestsListed(ListQuestsResponse response) {
        log.clear();
        for (QuestLogEntry entry : response.getQuestsList()) {
            log.put(entry.getQuestCode(), entry.getStatus());
        }
        // Đồng bộ lại ô "đang theo dõi" của HUD góc màn hình theo bản mới nhất, NẾU nó còn nhắc tới
        // đúng mã đó — tránh góc màn hình đứng yên với trạng thái CŨ trong khi nhật ký đầy đủ vừa có
        // bản MỚI hơn (VD nhiệm vụ hoàn thành đúng lúc cửa sổ nhật ký đang mở).
        if (isTracking() && log.containsKey(questCode)) {
            status = log.get(questCode);
        }
    }

    boolean isTracking() {
        return !questCode.isEmpty();
    }

    /** Đã xong nhưng chưa lĩnh thưởng — điều kiện DUY NHẤT để nút "Nhận thưởng" ở HUD góc màn hình bật. */
    boolean isClaimable() {
        return status == QuestStatus.QUEST_STATUS_COMPLETED;
    }

    /**
     * Đã xong nhưng chưa lĩnh thưởng, tra theo MỘT MÃ bất kỳ trong {@link #log} — khác
     * {@link #isClaimable()} (chỉ biết mã đang theo dõi trong PHIÊN CHƠI hiện tại).
     * {@link QuestLogPanel} dùng câu hỏi này để bày nút Nhận thưởng cho từng dòng, và nó trả lời
     * ĐÚNG kể cả sau relog — vì nó đọc {@link #log}, thứ nạp lại được từ {@code ListQuestsResponse}
     * chứ không từ bộ nhớ phiên chơi.
     */
    boolean isClaimable(String targetQuestCode) {
        return log.get(targetQuestCode) == QuestStatus.QUEST_STATUS_COMPLETED;
    }

    /** Có một lệnh nhận thưởng của ĐÚNG mã này đang chờ server trả lời hay không. */
    boolean isClaimPending(String targetQuestCode) {
        return !pendingClaimKey.isEmpty() && pendingClaimQuestCode.equals(targetQuestCode);
    }

    String questCode() {
        return questCode;
    }

    QuestStatus status() {
        return status;
    }

    /** Toàn bộ nhật ký, đúng thứ tự server trả về gần nhất — {@link QuestLogPanel} chỉ đọc. */
    List<LoggedQuest> questLog() {
        List<LoggedQuest> result = new ArrayList<>(log.size());
        for (Map.Entry<String, QuestStatus> entry : log.entrySet()) {
            result.add(new LoggedQuest(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(result);
    }

    /**
     * Khoá cho một lệnh nhận thưởng của HUD góc màn hình — tức mã ĐANG theo dõi. Tiện ích mỏng trên
     * {@link #claimKey(String)}, giữ nguyên chữ ký cũ cho {@link QuestCoordinator}.
     */
    String claimKey() {
        return claimKey(questCode);
    }

    /**
     * Khoá cho một lệnh nhận thưởng của MỘT MÃ bất kỳ (ADR-025, dùng chung cho cả HUD góc màn hình
     * lẫn từng dòng trong {@link QuestLogPanel}). Lần gọi đầu (hoặc gọi cho một mã KHÁC, hoặc gọi
     * lại sau khi lệnh trước đã có kết luận) sinh UUID mới; gọi lại cho ĐÚNG mã đang chờ trả về
     * NGUYÊN khoá cũ — server chỉ chặn được lệnh lặp khi thấy lại đúng khoá cũ đó.
     */
    String claimKey(String targetQuestCode) {
        if (!isClaimPending(targetQuestCode)) {
            pendingClaimQuestCode = targetQuestCode;
            pendingClaimKey = UUID.randomUUID().toString();
        }
        return pendingClaimKey;
    }

    /**
     * Lần nhận thưởng ĐANG CHỜ (bất kể sinh ra từ HUD góc màn hình hay từ một dòng trong nhật ký —
     * xem javadoc {@link #pendingClaimQuestCode}) đã có kết luận từ server.
     *
     * @param claimed true nếu phần thưởng đã thực sự về tay — mã đó chuyển {@code CLAIMED} trong cả
     *     {@link #log} lẫn (nếu trùng) ô "đang theo dõi". False thì giữ nguyên mọi trạng thái để
     *     người chơi bấm lại, nhưng khoá idempotency vẫn bị xoá: một lệnh đã có kết luận không còn
     *     là "lần thử lại" của lệnh cũ nữa.
     */
    void onClaimSettled(boolean claimed) {
        String settledQuestCode = pendingClaimQuestCode;
        pendingClaimQuestCode = "";
        pendingClaimKey = "";
        if (!claimed || settledQuestCode.isEmpty()) {
            return;
        }
        log.put(settledQuestCode, QuestStatus.QUEST_STATUS_CLAIMED);
        if (isTracking() && questCode.equals(settledQuestCode)) {
            status = QuestStatus.QUEST_STATUS_CLAIMED;
            questCode = "";
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Bảng tra khoá i18n theo GIÁ TRỊ ENUM — phủ hết, kể cả UNSPECIFIED.
    //
    // Khác quy ước MailController (loại UNSPECIFIED ra khỏi phép quét): bài học thật ghi trong nhật
    // ký bàn giao của dự án là một vòng gộp trước đây server thêm 6 outcome mà client không có khoá
    // tương ứng, người chơi chỉ đọc được "không rõ lỗi". Phủ hết MỌI giá trị .proto định nghĩa, kể cả
    // giá trị "không nên xuất hiện trên dây thật", là rào chắn rẻ nhất chống sự cố đó tái diễn.
    //
    // UNRECOGNIZED CỐ TÌNH vắng mặt ở cả hai bảng: nó không phải giá trị .proto định nghĩa mà là
    // artefact codegen cho số hiệu enum lạ (server MỚI HƠN client) — QuestTrackerTest lọc nó ra khi
    // quét toàn bộ enum. Cả hai hàm tra dưới đây trả Optional.empty() cho giá trị đó, và chỗ gọi
    // dịch thành "quest.status.unknown"/"quest.claim_outcome.unknown".
    // ---------------------------------------------------------------------------------------------

    private static final Map<QuestStatus, String> STATUS_MESSAGE_KEYS = Map.of(
            QuestStatus.QUEST_STATUS_UNSPECIFIED, "quest.status.unspecified",
            QuestStatus.QUEST_STATUS_IN_PROGRESS, "quest.status.in_progress",
            QuestStatus.QUEST_STATUS_COMPLETED, "quest.status.completed",
            QuestStatus.QUEST_STATUS_CLAIMED, "quest.status.claimed");

    private static final Map<ClaimQuestRewardOutcome, String> CLAIM_OUTCOME_MESSAGE_KEYS = Map.of(
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_UNSPECIFIED, "quest.claim_outcome.unspecified",
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_OK, "quest.claim_outcome.ok",
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_QUEST_NOT_FOUND, "quest.claim_outcome.quest_not_found",
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_NOT_ACCEPTED, "quest.claim_outcome.not_accepted",
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_NOT_COMPLETED_YET,
                    "quest.claim_outcome.not_completed_yet",
            ClaimQuestRewardOutcome.CLAIM_QUEST_REWARD_OUTCOME_DUPLICATE_REQUEST,
                    "quest.claim_outcome.duplicate_request");

    /** Khoá i18n cho một trạng thái nhiệm vụ — dùng để hiện chữ trong {@link QuestLogPanel}. */
    static Optional<String> statusMessageKey(QuestStatus status) {
        return Optional.ofNullable(STATUS_MESSAGE_KEYS.get(status));
    }

    /**
     * Khoá i18n cho một outcome nhận thưởng — cho phép nói kết quả CỤ THỂ ("chưa hoàn thành", "đã xử
     * lý trước đó"...) thay vì chỉ hai câu chung chung "xong"/"hỏng".
     */
    static Optional<String> claimOutcomeMessageKey(ClaimQuestRewardOutcome outcome) {
        return Optional.ofNullable(CLAIM_OUTCOME_MESSAGE_KEYS.get(outcome));
    }

    /**
     * Rời màn hình/mất kết nối — nhật ký của một phiên chơi không sống sót sang phiên sau. Server là
     * nguồn chân lý; {@code WorldCommandSender#listQuests()} sẽ hỏi lại khi vào world mới, đúng luật
     * áp cho cả {@link #log} lẫn ô "đang theo dõi".
     */
    void reset() {
        questCode = "";
        status = QuestStatus.QUEST_STATUS_UNSPECIFIED;
        log.clear();
        pendingClaimQuestCode = "";
        pendingClaimKey = "";
    }
}
