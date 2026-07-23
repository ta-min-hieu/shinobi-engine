package dev.kage.quest.domain;

import java.util.List;
import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code quest_template} — PHÁT ĐỌC THUẦN, không ghi (config nội dung quest). */
public interface QuestTemplatePort {

    /** {@code Optional.empty()} nếu {@code questCode} không tồn tại. */
    Optional<QuestTemplate> findByCode(String questCode);

    /**
     * Toàn bộ {@code script_ref} đang được tham chiếu — dùng cho validate script lúc khởi động
     * (SCRIPTING.md §8), KHÔNG dùng trong gameplay. {@code default} rỗng để Fake/lambda hiện có
     * (chỉ implement {@link #findByCode}) không bị vỡ compile — chỉ adapter Postgres thật cần ghi
     * đè phương thức này.
     */
    default List<String> listAllScriptRefs() {
        return List.of();
    }
}
