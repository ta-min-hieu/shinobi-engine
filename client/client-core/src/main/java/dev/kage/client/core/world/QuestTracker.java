package dev.kage.client.core.world;

import java.util.UUID;

import dev.kage.protocol.QuestStatus;

/**
 * Nhiệm vụ đang theo dõi — mã, trạng thái, và khoá idempotency của lần nhận thưởng (2026-08-09).
 * Thuần Java, không GL, test được thẳng (UI.md §4).
 *
 * <p>⚠️ <b>Chỉ sống trong phiên chơi, và đó là giới hạn của GIAO THỨC chứ không phải lựa chọn ở
 * đây</b>: quest.proto có đúng 4 opcode (606-609) và <b>không có cái nào hỏi được "tôi đang làm
 * nhiệm vụ gì"</b>. Server nhớ đủ (bảng {@code character_quest}), client thì không có đường nào lấy
 * lại sau khi đăng nhập lại — kể cả {@code quest_code} để mà nhận thưởng. Vá chỗ này phải THÊM
 * opcode, tức đổi protocol, tức cần chủ dự án duyệt (/CLAUDE.md §4) — không tự làm.
 *
 * <p>Mã nhiệm vụ cũng không đến từ gói trả lời: {@code AcceptQuestResponse} chỉ có {@code outcome}.
 * Nó được đọc từ {@code action} của lựa chọn hội thoại ({@code "accept_quest:<code>"}) — xem
 * {@link DialogueController#optionAction}. Đây là cách dùng HỢP LỆ của trường đó theo dialogue.proto
 * ("chỉ để client hiển thị"); cái bị cấm là gửi nó NGƯỢC lại server, và điều đó vẫn không xảy ra.
 */
final class QuestTracker {

    private String questCode = "";
    private QuestStatus status = QuestStatus.QUEST_STATUS_UNSPECIFIED;

    /**
     * Khoá idempotency của lần nhận thưởng đang diễn ra (ADR-025) — sinh MỘT lần rồi giữ nguyên qua
     * mọi lần thử lại của CÙNG một lần nhận, xoá khi đã xong. Sinh mới mỗi lần bấm sẽ biến chính cơ
     * chế chống trùng thành vô dụng: server chỉ chặn được khi thấy lại đúng khoá cũ.
     */
    private String pendingClaimKey = "";

    /** Nhiệm vụ vừa được nhận (server đã trả {@code ACCEPT_QUEST_OUTCOME_OK}). */
    void onAccepted(String questCode) {
        this.questCode = questCode;
        this.status = QuestStatus.QUEST_STATUS_IN_PROGRESS;
        this.pendingClaimKey = "";
    }

    /**
     * Server báo nhiệm vụ đổi trạng thái. Chỉ nhận nếu đúng nhiệm vụ đang theo dõi — người chơi có
     * thể đang có nhiều nhiệm vụ phía server (không có cách nào biết, xem javadoc lớp), và một sự
     * kiện của nhiệm vụ khác không được ghi đè cái đang hiển thị.
     */
    void onStatusChanged(String eventQuestCode, QuestStatus newStatus) {
        if (isTracking() && questCode.equals(eventQuestCode)) {
            status = newStatus;
        }
    }

    boolean isTracking() {
        return !questCode.isEmpty();
    }

    /** Đã xong nhưng chưa lĩnh thưởng — điều kiện DUY NHẤT để nút "Nhận thưởng" bật. */
    boolean isClaimable() {
        return status == QuestStatus.QUEST_STATUS_COMPLETED;
    }

    String questCode() {
        return questCode;
    }

    QuestStatus status() {
        return status;
    }

    /**
     * Khoá cho một lệnh nhận thưởng. Lần gọi đầu sinh UUID mới; các lần sau trả LẠI đúng khoá đó cho
     * tới khi {@link #onClaimSettled}.
     */
    String claimKey() {
        if (pendingClaimKey.isEmpty()) {
            pendingClaimKey = UUID.randomUUID().toString();
        }
        return pendingClaimKey;
    }

    /**
     * Lần nhận thưởng đã có kết luận từ server (thành công, hoặc trùng lệnh, hoặc hỏng hẳn).
     *
     * @param claimed true nếu phần thưởng đã thực sự về tay — nhiệm vụ chuyển {@code CLAIMED} và
     *     thôi theo dõi. False thì giữ nguyên trạng thái để người chơi bấm lại, nhưng khoá
     *     idempotency vẫn bị xoá: một lệnh đã có kết luận không còn là "lần thử lại" của lệnh cũ nữa.
     */
    void onClaimSettled(boolean claimed) {
        pendingClaimKey = "";
        if (claimed) {
            status = QuestStatus.QUEST_STATUS_CLAIMED;
            questCode = "";
        }
    }
}
