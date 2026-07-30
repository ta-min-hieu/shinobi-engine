package dev.kage.engine.ecs.system;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;

/**
 * Di chuyển entity theo vận tốc mỗi tick (ECS.md §4). Lưu vị trí trước để render nội suy
 * (RENDERING.md §7). Không {@code new} object trong {@code processEntity} (Điều 31).
 */
public final class MovementSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        PositionComponent pos = POSITION.get(entity);
        VelocityComponent vel = VELOCITY.get(entity);

        pos.prevX = pos.x;
        pos.prevY = pos.y;
        pos.x += vel.x * deltaTime;
        pos.y += vel.y * deltaTime;
    }
}
