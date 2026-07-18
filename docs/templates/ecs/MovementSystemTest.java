// TEMPLATE — Test ECS System headless. Chỉ add 1 System cần test (cô lập — [TESTING §5]).
package dev.kage.engine.ecs.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.badlogic.ashley.core.ComponentMapper;
import com.badlogic.ashley.core.Engine;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import org.junit.jupiter.api.Test;

class MovementSystemTest {

    @Test
    void shouldMoveEntity_byVelocityTimesDelta() {
        var engine = new Engine();
        engine.addSystem(new MovementSystem());     // CHỈ 1 system — biết chắc cái gì gây kết quả

        var e = engine.createEntity();
        var pos = new PositionComponent(); pos.x = 0; pos.y = 0;
        var vel = new VelocityComponent(); vel.x = 10; vel.y = 0;
        e.add(pos);
        e.add(vel);
        engine.addEntity(e);

        engine.update(1.0f);                        // 1 giây

        var posMapper = ComponentMapper.getFor(PositionComponent.class);
        assertThat(posMapper.get(e).x).isCloseTo(10f, within(0.001f));
    }
}
