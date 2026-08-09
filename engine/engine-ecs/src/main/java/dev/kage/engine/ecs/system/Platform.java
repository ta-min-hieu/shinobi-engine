package dev.kage.engine.ecs.system;

/**
 * Mặt bệ tĩnh mà entity có thể đứng lên (ADR-033 — "lên bệ cao" có tác dụng gameplay thật, PHYSICS.md
 * §mới). {@code footprint} là vùng {@code (x, y)} bệ phủ tới (tái dùng {@link Aabb}, không phải vật
 * chặn ngang như {@link TerrainCollisionSystem} dùng — cùng kiểu dữ liệu, khác cách {@link
 * VerticalMovementSystem} diễn giải), {@code topZ} là độ cao mặt bệ.
 *
 * <p>Danh sách platform mặc định RỖNG ở mọi World hiện có (chưa có map pilot nào định nghĩa bệ —
 * ADR-033 "Việc cần làm tiếp theo" #7, việc art/level design riêng) — mặt đất phẳng {@code z=0} LUÔN
 * là bệ ngầm định, không cần khai báo (xem {@link VerticalMovementSystem#groundSurfaceBelow}).
 */
public record Platform(Aabb footprint, float topZ) {

    /** Điểm {@code (x, y)} có nằm trong vùng phủ của bệ không (không xét độ cao). */
    public boolean contains(float x, float y) {
        return footprint.contains(x, y);
    }
}
