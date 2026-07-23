package dev.kage.quest.domain;

import java.util.Optional;

/** Cổng đọc dữ liệu tĩnh {@code quest_template} — PHÁT ĐỌC THUẦN, không ghi (config nội dung quest). */
public interface QuestTemplatePort {

    /** {@code Optional.empty()} nếu {@code questCode} không tồn tại. */
    Optional<QuestTemplate> findByCode(String questCode);
}
