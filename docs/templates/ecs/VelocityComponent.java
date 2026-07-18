// TEMPLATE — Component vận tốc (world unit/giây).
package dev.kage.engine.ecs.component;

import com.badlogic.ashley.core.Component;

/** Vận tốc entity. Mutable + public field: ngoại lệ có chủ đích ([ECS §3.1]). */
public final class VelocityComponent implements Component {
    public float x, y;
}
