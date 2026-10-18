package dev.kage.achievement.domain;

/**
 * Một thành tựu (bảng {@code achievement}, ADR-062).
 *
 * @param code khoá nghiệp vụ
 * @param kind loại mốc
 * @param target ngưỡng phải đạt, {@code >= 1}
 * @param nameKey khoá i18n tên thành tựu
 * @param titleKey khoá i18n danh hiệu mở ra
 */
public record Achievement(String code, AchievementKind kind, long target, String nameKey, String titleKey) {

    public Achievement {
        if (target < 1) {
            throw new IllegalArgumentException("target phải >= 1: " + code);
        }
    }

    /** Giá trị {@code value} của đúng loại này đã đạt mốc chưa. */
    public boolean reachedBy(long value) {
        return value >= target;
    }
}
