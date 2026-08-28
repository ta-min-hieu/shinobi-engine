package dev.kage.quest.domain;

/**
 * Một dòng nhật ký nhiệm vụ (ADR-035 Decision-4): mã nhiệm vụ + trạng thái, KHÔNG hơn.
 *
 * <p>Khác {@link QuestProgress} ở hai điểm cố ý:
 *
 * <ul>
 *   <li>mang {@code questCode} (khoá nghiệp vụ client dùng) thay vì {@code questTemplateId} —
 *       {@code ClaimQuestRewardCommand} nhận code, không nhận id;
 *   <li>KHÔNG mang {@code progressJson}: chuỗi ĐỤC do script tự quản, Java không parse và client
 *       không hiển thị được. Mang nó qua dây là phá ranh giới script/Java để đổi lấy con số không.
 * </ul>
 */
public record QuestLogEntry(String questCode, QuestStatus status) {

    public QuestLogEntry {
        if (questCode == null || questCode.isBlank()) {
            throw new IllegalArgumentException("questCode không được rỗng");
        }
        if (status == null) {
            throw new IllegalArgumentException("status không được null");
        }
    }
}
