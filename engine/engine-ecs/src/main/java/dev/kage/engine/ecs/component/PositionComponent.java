package dev.kage.engine.ecs.component;

import dev.kage.engine.ecs.Component;

/**
 * Vị trí entity (world unit = mét — RENDERING.md §4).
 *
 * <p>Mutable + field public: ngoại lệ có chủ đích của Điều 43 (ECS.md §3.1). Lưu cả vị trí tick
 * trước ({@code prevX/prevY}) để client nội suy hình ảnh mượt giữa 2 tick (RENDERING.md §7).
 */
public final class PositionComponent implements Component {
    public float x;
    public float y;
    public float prevX;
    public float prevY;
}
