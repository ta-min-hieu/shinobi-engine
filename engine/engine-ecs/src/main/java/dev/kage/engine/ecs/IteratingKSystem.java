package dev.kage.engine.ecs;

/**
 * Base class cho System xử lý mọi Entity thuộc một Family, mỗi tick (ECS.md §4).
 *
 * <p>Chỉ file này bên trong package {@code dev.kage.engine.ecs} tạo Ashley
 * {@code IteratingSystem} thật — subclass ở server-world/client-core/feature-* chỉ thấy
 * {@link KEntity}, không bao giờ thấy {@code com.badlogic.ashley.core.Entity}.
 *
 * <pre>{@code
 * public final class MovementSystem extends IteratingKSystem {
 *     private static final ComponentMapper<PositionComponent> POS = ComponentMapper.getFor(PositionComponent.class);
 *     private static final ComponentMapper<VelocityComponent> VEL = ComponentMapper.getFor(VelocityComponent.class);
 *
 *     public MovementSystem() {
 *         super(Family.all(PositionComponent.class, VelocityComponent.class).get());
 *     }
 *
 *     protected void processEntity(KEntity entity, float deltaTime) {
 *         var pos = POS.get(entity);
 *         var vel = VEL.get(entity);
 *         pos.x += vel.x * deltaTime;
 *         pos.y += vel.y * deltaTime;
 *     }
 * }
 * }</pre>
 */
public abstract class IteratingKSystem {

    private final AshleyAdapter ashleySystem;
    private EcsWorld world;

    protected IteratingKSystem(Family family) {
        this.ashleySystem = new AshleyAdapter(family.raw(), this);
    }

    /** System không giữ state của entity — state ở Component (ECS.md §4). */
    protected abstract void processEntity(KEntity entity, float deltaTime);

    /** Chỉ EcsWorld gọi lúc addSystem — không phải Service Locator (ENGINE.md §6). */
    void bind(EcsWorld world) {
        this.world = world;
    }

    com.badlogic.ashley.systems.IteratingSystem ashleySystem() {
        return ashleySystem;
    }

    private static final class AshleyAdapter extends com.badlogic.ashley.systems.IteratingSystem {
        private final IteratingKSystem outer;

        AshleyAdapter(com.badlogic.ashley.core.Family family, IteratingKSystem outer) {
            super(family);
            this.outer = outer;
        }

        @Override
        protected void processEntity(com.badlogic.ashley.core.Entity entity, float deltaTime) {
            // Không "new KEntity" mỗi lần — EcsWorld cache wrapper theo entity (Điều 31).
            outer.processEntity(outer.world.wrap(entity), deltaTime);
        }
    }
}
