package dev.kage.inventory.domain;

/**
 * Cấp phi kiếm (ADR-203) — lưu ở {@code enhance_level} của dòng phi kiếm (cường hoá thường đã loại ô MOUNT, nên cột đó
 * luôn trống với phi kiếm). Mỗi cấp cộng thêm {@link #SPEED_PER_LEVEL}% tốc độ, tổng không quá {@link #MAX_SPEED}%.
 * Mọi con số là tham số đề xuất (Điều 27).
 */
public final class MountPolicy {

    /** Cấp cao nhất (0 = chưa nâng). */
    public static final int MAX_LEVEL = 5;

    /** Phần trăm tốc độ cộng thêm mỗi cấp. */
    public static final int SPEED_PER_LEVEL = 5;

    /** Trần tổng tốc độ — khớp CHECK {@code move_speed_percent BETWEEN 0 AND 100} của V71. */
    public static final int MAX_SPEED = 100;

    private MountPolicy() {}

    /** Tốc độ của một phi kiếm gốc {@code basePercent} ở cấp {@code level}. */
    public static int speed(int basePercent, int level) {
        int bounded = Math.max(0, Math.min(MAX_LEVEL, level));
        return Math.min(MAX_SPEED, basePercent + SPEED_PER_LEVEL * bounded);
    }
}
