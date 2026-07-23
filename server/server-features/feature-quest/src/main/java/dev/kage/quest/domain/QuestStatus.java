package dev.kage.quest.domain;

/**
 * Trạng thái tiến độ quest (DATABASE.md §2.5). {@code NOT_STARTED} KHÔNG có giá trị enum tương ứng
 * — không có dòng {@code character_quest_progress} nghĩa là chưa nhận, giống cách "chưa mặc đồ"
 * không cần dòng {@code equipment_slot}.
 */
public enum QuestStatus {
    IN_PROGRESS, COMPLETED, CLAIMED
}
