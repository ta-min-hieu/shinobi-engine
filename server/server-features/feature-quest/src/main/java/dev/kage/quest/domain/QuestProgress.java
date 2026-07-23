package dev.kage.quest.domain;

/**
 * Tiến độ 1 quest của 1 nhân vật. {@code progressJson} là TEXT ĐỤC với Java (không parse) — script
 * tự {@code JSON.stringify}/{@code JSON.parse} (GraalJS built-in, xác nhận bằng test thật ở
 * engine-scripting), Java chỉ lưu/trả lại nguyên văn.
 */
public record QuestProgress(long characterId, long questTemplateId, QuestStatus status, String progressJson) {

    public QuestProgress {
        if (characterId <= 0) {
            throw new IllegalArgumentException("characterId phải > 0: " + characterId);
        }
        if (questTemplateId <= 0) {
            throw new IllegalArgumentException("questTemplateId phải > 0: " + questTemplateId);
        }
        if (progressJson == null) {
            throw new IllegalArgumentException("progressJson không được null");
        }
    }
}
