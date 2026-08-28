package dev.kage.quest.domain;

import java.util.List;
import java.util.Optional;

/** Cổng lưu trữ tiến độ quest ({@code character_quest_progress}). */
public interface QuestProgressPort {

    /** {@code Optional.empty()} nghĩa là NOT_STARTED — chưa nhận quest này (không có dòng nào). */
    Optional<QuestProgress> find(long characterId, long questTemplateId);

    /** Upsert theo khoá {@code (characterId, questTemplateId)} — luôn ghi trong transaction đang mở. */
    void save(Transaction tx, QuestProgress progress);

    /**
     * MỌI dòng nhật ký của nhân vật, GỒM CẢ {@code CLAIMED} (ADR-035 Decision-4) — lọc phía server
     * thì client không phân biệt được "chưa từng nhận" với "đã trả xong". Trả {@code questCode}
     * (adapter tự join {@code quest_template}), không phải {@code questTemplateId}.
     *
     * <p>{@code default} rỗng để Fake/anonymous class hiện có không vỡ compile — cùng lý do đã ghi
     * ở {@link QuestTemplatePort#listAllScriptRefs}.
     */
    default List<QuestLogEntry> listLog(long characterId) {
        return List.of();
    }
}
