package dev.kage.quest.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kage.quest.domain.QuestLogEntry;
import dev.kage.quest.domain.QuestProgressPort;

/**
 * Nhật ký nhiệm vụ của một nhân vật (ADR-035 Decision-4) — đọc thuần, không ghi, không cần
 * transaction.
 *
 * <p>Đây là gói GỠ BUG THẬT, không phải tiện nghi: trước nó, nguồn duy nhất client biết
 * {@code quest_code} là chuỗi {@code action="accept_quest:<code>"} đọc được ngay lúc bấm trong hội
 * thoại. Đăng xuất là mất, mà {@code ClaimQuestRewardCommand} lại bắt buộc có code → nhận nhiệm vụ
 * rồi đăng nhập lại là KHÔNG lĩnh thưởng được nữa, vĩnh viễn.
 */
public final class ListQuestsUseCase {

    private final QuestProgressPort progressPort;

    public ListQuestsUseCase(QuestProgressPort progressPort) {
        this.progressPort = progressPort;
    }

    /** GỒM CẢ quest đã {@code CLAIMED} — xem {@link QuestProgressPort#listLog} vì sao không lọc. */
    public List<QuestLogEntry> execute(long characterId) {
        return progressPort.listLog(characterId);
    }

    /**
     * {@code questCode → progress_json} của mọi quest ĐANG LÀM (dòng 65(c) mở rộng, 2026-10-17) — cho
     * script hội thoại đọc, CHỈ ĐỌC. Chuỗi vẫn đục với Java: không parse, không kiểm.
     */
    public Map<String, String> inProgressJson(long characterId) {
        Map<String, String> progressByCode = new HashMap<>();
        for (QuestProgressPort.ActiveQuest active : progressPort.listInProgress(characterId)) {
            progressByCode.put(active.questCode(), active.progressJson());
        }
        return progressByCode;
    }
}
