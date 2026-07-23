package dev.kage.quest.domain;

import java.util.Optional;

/** Cổng lưu trữ tiến độ quest ({@code character_quest_progress}). */
public interface QuestProgressPort {

    /** {@code Optional.empty()} nghĩa là NOT_STARTED — chưa nhận quest này (không có dòng nào). */
    Optional<QuestProgress> find(long characterId, long questTemplateId);

    /** Upsert theo khoá {@code (characterId, questTemplateId)} — luôn ghi trong transaction đang mở. */
    void save(Transaction tx, QuestProgress progress);
}
