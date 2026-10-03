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

    /**
     * Đoạn thẳng {@code (x0, y0) → (x1, y1)} có chạm hình chữ nhật (kể cả biên) ở bất kỳ điểm nào
     * không — phép quét chống xuyên tường của {@link TerrainCollisionSystem}. Thuật toán cắt slab
     * (Liang–Barsky): thu hẹp khoảng tham số {@code [0, 1]} theo từng trục; còn khoảng không rỗng
     * thì đoạn chạm hộp. Chỉ dùng biến cục bộ nguyên thuỷ — không cấp phát (Điều 31).
     */
    public boolean intersectsSegment(float x0, float y0, float x1, float y1) {
        float dx = x1 - x0;
        float dy = y1 - y0;
        float enter = 0f;
        float exit = 1f;
        if (dx == 0f) {
            if (x0 < minX || x0 > maxX) {
                return false;
            }
        } else {
            float tA = (minX - x0) / dx;
            float tB = (maxX - x0) / dx;
            enter = Math.max(enter, Math.min(tA, tB));
            exit = Math.min(exit, Math.max(tA, tB));
            if (enter > exit) {
                return false;
            }
        }
        if (dy == 0f) {
            return y0 >= minY && y0 <= maxY;
        }
        float tA = (minY - y0) / dy;
        float tB = (maxY - y0) / dy;
        enter = Math.max(enter, Math.min(tA, tB));
        exit = Math.min(exit, Math.max(tA, tB));
        return enter <= exit;
    }
}
