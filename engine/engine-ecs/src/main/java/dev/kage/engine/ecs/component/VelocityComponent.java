package dev.kage.engine.ecs.component;

import dev.kage.engine.ecs.Component;

/** Vận tốc entity (world unit/giây). Mutable + field public: ngoại lệ có chủ đích (ECS.md §3.1). */
public final class VelocityComponent implements Component {
    public float x;
    public float y;
}
