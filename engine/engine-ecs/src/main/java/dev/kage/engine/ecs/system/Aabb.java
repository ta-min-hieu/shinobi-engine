package dev.kage.engine.ecs.system;

/**
 * Hình chữ nhật tĩnh trục-thẳng (world unit = mét — PHYSICS.md §5), dùng làm obstacle cho
 * {@link TerrainCollisionSystem}. {@code min} luôn nhỏ hơn hoặc bằng {@code max} theo cả 2 trục —
 * người gọi tự đảm bảo (không validate ở đây, tránh chi phí ở đường dựng dữ liệu map).
 */
public record Aabb(float minX, float minY, float maxX, float maxY) {

    /** Điểm {@code (x, y)} có nằm trong hình chữ nhật (kể cả biên) không. */
    public boolean contains(float x, float y) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY;
    }
}
