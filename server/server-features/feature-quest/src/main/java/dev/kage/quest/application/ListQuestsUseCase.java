package dev.kage.quest.application;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.kage.quest.domain.DailyReset;
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
    private final Clock clock;

    public ListQuestsUseCase(QuestProgressPort progressPort) {
        this(progressPort, Clock.systemUTC());
    }

    /** {@code clock} để test đứng được hai bên mốc 0h của nhiệm vụ ngày (ADR-055). */
    public ListQuestsUseCase(QuestProgressPort progressPort, Clock clock) {
        this.progressPort = progressPort;
        this.clock = clock;
    }

    /** Nhiệm vụ ngày đã lĩnh trước 0h hôm nay KHÔNG có mặt — với người chơi đó là "chưa nhận" (ADR-055). */
    public List<QuestLogEntry> execute(long characterId) {
        return progressPort.listLog(characterId, DailyReset.startOfDay(clock.instant()));
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
