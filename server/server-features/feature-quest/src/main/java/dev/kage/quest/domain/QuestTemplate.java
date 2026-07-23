package dev.kage.quest.domain;

/** Dữ liệu TĨNH của 1 quest (DATABASE.md §2.5) — chỉ mang những gì UseCase cần (YAGNI, chưa đọc
 * {@code type}/{@code prerequisite_quest_id}, thêm khi có nhu cầu thật). */
public record QuestTemplate(long id, String scriptRef) {

    public QuestTemplate {
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
        if (scriptRef == null || scriptRef.isBlank()) {
            throw new IllegalArgumentException("scriptRef không được rỗng");
        }
    }
}
