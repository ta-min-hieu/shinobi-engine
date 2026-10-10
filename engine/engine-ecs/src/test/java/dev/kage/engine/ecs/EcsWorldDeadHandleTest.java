package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;

/**
 * Handle của entity đã gỡ không được đọc hay ghi sang entity KHÁC.
 *
 * <p>Gốc lỗi (tìm ra bởi {@link EcsWorldModelPropertyTest}, 2026-10-10): {@code PooledEngine} của
 * Ashley trả entity gốc đã gỡ về pool rồi cấp lại nó cho {@code createEntity()} kế tiếp. Handle cũ
 * còn giữ entity gốc đó, nên (1) ghi qua handle cũ lúc entity gốc còn nằm trong pool là cài sẵn
 * component cho một entity CHƯA ra đời — nó sinh ra đã thuộc Family mà không ai gắn cho nó; (2) sau
 * khi entity gốc được cấp lại, đọc/ghi qua handle cũ là đọc/ghi thẳng vào entity mới.
 * {@code EcsWorldLifecycleTest} đã chặn {@code removeEntity} qua handle cũ; lớp này chặn nốt
 * {@code add}, {@code has} và {@code ComponentMapper}.
 */
class EcsWorldDeadHandleTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);
    private static final ComponentMapper<VelocityComponent> VELOCITY = ComponentMapper.getFor(VelocityComponent.class);

    /** Ghi lại id mọi entity có vận tốc — Family mà entity "ma" lọt vào. */
    private static final class VelocityRecorder extends IteratingKSystem {
        final List<Long> processedIds = new ArrayList<>();

        VelocityRecorder() {
            super(Family.all(VelocityComponent.class).get());
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            processedIds.add(entity.id());
        }
    }

    @Test
    void shouldNotPlantComponentOnTheNextEntity_whenDeadHandleIsWrittenBeforeRecycling() {
        EcsWorld world = new EcsWorld();
        VelocityRecorder recorder = new VelocityRecorder();
        world.addSystem(recorder);
        KEntity dead = world.createEntity().add(new PositionComponent());
        world.removeEntity(dead);

        dead.add(new VelocityComponent()); // entity gốc đang nằm trong pool của Ashley
        KEntity fresh = world.createEntity(); // ...và được cấp lại ở đây
        world.update(1f);

        assertThat(fresh.has(VelocityComponent.class)).isFalse();
        assertThat(VELOCITY.has(fresh)).isFalse();
        assertThat(recorder.processedIds).isEmpty();
    }

    @Test
    void shouldNotWriteIntoRecycledEntity_whenDeadHandleIsWrittenAfterRecycling() {
        EcsWorld world = new EcsWorld();
        world.addSystem(new MovementSystem());
        KEntity dead = world.createEntity().add(new PositionComponent());
        world.removeEntity(dead);
        PositionComponent freshPosition = new PositionComponent();
        KEntity fresh = world.createEntity().add(freshPosition);
        VelocityComponent strayVelocity = new VelocityComponent();
        strayVelocity.x = 5f;

        dead.add(strayVelocity).add(new PositionComponent());
        world.update(1f);

        assertThat(POSITION.get(fresh)).isSameAs(freshPosition);
        assertThat(VELOCITY.has(fresh)).isFalse();
        assertThat(freshPosition.x).isZero();
    }

    @Test
    void shouldLookEmpty_whenDeadHandleIsReadAfterRecycling() {
        EcsWorld world = new EcsWorld();
        KEntity dead = world.createEntity().add(new PositionComponent());
        world.removeEntity(dead);
        world.createEntity().add(new PositionComponent()).add(new VelocityComponent());

        assertThat(dead.has(PositionComponent.class)).isFalse();
        assertThat(POSITION.has(dead)).isFalse();
        assertThat(POSITION.get(dead)).isNull();
        assertThat(VELOCITY.has(dead)).isFalse();
    }

    @Test
    void shouldTreatHandlesFromBeforeClearAsDead() {
        EcsWorld world = new EcsWorld();
        KEntity previousSession = world.createEntity().add(new PositionComponent());
        world.clear();
        PositionComponent nextPosition = new PositionComponent();
        KEntity nextSession = world.createEntity().add(nextPosition);

        previousSession.add(new VelocityComponent());

        assertThat(previousSession.has(PositionComponent.class)).isFalse();
        assertThat(POSITION.get(previousSession)).isNull();
        assertThat(VELOCITY.has(nextSession)).isFalse();
        assertThat(POSITION.get(nextSession)).isSameAs(nextPosition);
    }

    /** Gỡ giữa lượt lặp là gỡ HOÃN: handle còn sống trọn lượt của System đang chạy, chết ngay sau đó. */
    @Test
    void shouldKeepHandleAliveUntilTheRunningSystemFinishes_whenRemovedMidIteration() {
        EcsWorld world = new EcsWorld();
        List<Boolean> readableDuringPass = new ArrayList<>();
        world.addSystem(new IteratingKSystem(Family.all(PositionComponent.class).get()) {
            @Override
            protected void processEntity(KEntity entity, float deltaTime) {
                world.removeEntity(entity);
                readableDuringPass.add(POSITION.has(entity));
            }
        });
        KEntity entity = world.createEntity().add(new PositionComponent());

        world.update(1f);

        assertThat(readableDuringPass).containsExactly(true);
        assertThat(POSITION.has(entity)).isFalse();
        assertThat(world.entityCount()).isZero();
    }

    @Test
    void shouldIgnoreHandleOfAnotherWorld_whenRemoving() {
        EcsWorld world = new EcsWorld();
        EcsWorld otherWorld = new EcsWorld();
        world.createEntity().add(new PositionComponent());
        KEntity foreign = otherWorld.createEntity().add(new PositionComponent());

        world.removeEntity(foreign);

        assertThat(world.entityCount()).isEqualTo(1);
        assertThat(otherWorld.entityCount()).isEqualTo(1);
        assertThat(POSITION.has(foreign)).isTrue();
    }
}
