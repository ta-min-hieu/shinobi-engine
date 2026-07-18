// TEMPLATE — ECS System. TOÀN BỘ gameplay logic ở đây. Không giữ state của entity (state ở Component).
package dev.kage.engine.ecs.system;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Entity;
import com.badlogic.ashley.core.Family;
import com.badlogic.ashley.systems.IteratingSystem;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;

/**
 * Di chuyển entity theo vận tốc. Chỉ xử lý entity thuộc Family của nó.
 *
 * <p>Thứ tự tick CÓ Ý NGHĨA — System này chạy trước CollisionSystem ([ECS §6]).
 * Đổi thứ tự = đổi behavior, coi như thay đổi kiến trúc.
 */
public final class MovementSystem extends IteratingSystem {

    // ComponentMapper: cache index, nhanh hơn getComponent() nhiều — bắt buộc trong hot path ([ECS §5]).
    private final ComponentMapper<PositionComponent> posMapper =
            ComponentMapper.getFor(PositionComponent.class);
    private final ComponentMapper<VelocityComponent> velMapper =
            ComponentMapper.getFor(VelocityComponent.class);

    public MovementSystem() {
        super(Family.all(PositionComponent.class, VelocityComponent.class).get());
    }

    @Override
    protected void processEntity(Entity entity, float deltaTime) {
        var pos = posMapper.get(entity);
        var vel = velMapper.get(entity);

        // ❌ KHÔNG new object ở đây (Điều 31) — ghi thẳng vào field Component.
        pos.prevX = pos.x;                 // lưu vị trí trước cho interpolation ([RENDERING §7])
        pos.prevY = pos.y;
        pos.x += vel.x * deltaTime;
        pos.y += vel.y * deltaTime;
    }
}
