// TEMPLATE — Component vị trí. Lưu cả prev cho interpolation ([RENDERING §7]).
package dev.kage.engine.ecs.component;

import com.badlogic.ashley.core.Component;

/** Vị trí entity (world unit = mét). Mutable + public field: ngoại lệ có chủ đích ([ECS §3.1]). */
public final class PositionComponent implements Component {
    public float x, y;
    public float prevX, prevY;   // tick trước — render nội suy giữa prev và hiện tại
}
