package dev.kage.quest.domain;

import java.util.OptionalLong;

/**
 * Dữ liệu TĨNH của 1 quest (DATABASE.md §2.5) — chỉ mang những gì UseCase cần (YAGNI; {@code type}
 * vẫn chưa ai đọc, thêm khi có nhu cầu thật).
 *
 * <p>{@code prerequisiteQuestId} là ADR-044: cột {@code prerequisite_quest_id} có từ V11 nhưng
 * KHÔNG ai đọc suốt từ đó, cho tới khi V28 (2026-09-05) trở thành dòng dữ liệu ĐẦU TIÊN dùng nó.
 * {@link OptionalLong} chứ không phải {@code Long} có thể null — Điều 11 cấm trả {@code null}, và
 * kiểu này nói thẳng ra rằng "không có quest tiên quyết" là trạng thái HỢP LỆ, không phải thiếu dữ
 * liệu.
 */
public record QuestTemplate(long id, String scriptRef, OptionalLong prerequisiteQuestId, RepeatPolicy repeatPolicy) {

    /** Quest một lần — hình dạng trước ADR-055, giữ cho mọi chỗ dựng quest chính tuyến. */
    public QuestTemplate(long id, String scriptRef, OptionalLong prerequisiteQuestId) {
        this(id, scriptRef, prerequisiteQuestId, RepeatPolicy.ONCE);
    }

    public QuestTemplate {
        if (repeatPolicy == null) {
            throw new IllegalArgumentException("repeatPolicy không được null");
        }
        if (id <= 0) {
            throw new IllegalArgumentException("id phải > 0: " + id);
        }
        if (scriptRef == null || scriptRef.isBlank()) {
            throw new IllegalArgumentException("scriptRef không được rỗng");
        }
        if (prerequisiteQuestId == null) {
            throw new IllegalArgumentException("prerequisiteQuestId không được null — dùng OptionalLong.empty()");
        }
        if (prerequisiteQuestId.isPresent() && prerequisiteQuestId.getAsLong() == id) {
            throw new IllegalArgumentException("quest không thể là tiên quyết của chính nó: " + id);
        }
    }

    /** Nhiệm vụ ngày (ADR-055). */
    public boolean isDaily() {
        return repeatPolicy == RepeatPolicy.DAILY;
    }

    /** Quest KHÔNG có điều kiện mở khoá — đường thường gặp nhất, và là hành vi trước ADR-044. */
    public static QuestTemplate withoutPrerequisite(long id, String scriptRef) {
        return new QuestTemplate(id, scriptRef, OptionalLong.empty());
    }
}
