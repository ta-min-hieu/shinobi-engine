package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Vòng đời entity của {@link EcsWorld}: gỡ entity giữa lúc System đang lặp, và gỡ bằng một
 * {@link KEntity} đã hết hạn.
 *
 * <p>Hai lỗi thật, cùng gốc là {@code PooledEngine} của Ashley: (1) ngoài {@code Engine.update}
 * Ashley gỡ entity NGAY, và mảng Family là mảng KHÔNG giữ thứ tự (gỡ = kéo phần tử cuối vào chỗ
 * trống), nên gỡ một entity giữa vòng lặp làm entity cuối mất lượt cả tick; (2) entity gốc đã gỡ
 * được trả về pool rồi cấp lại cho {@code createEntity} kế tiếp, nên một handle cũ trỏ vào entity
 * gốc của một entity MỚI hoàn toàn khác.
 */
class EcsWorldLifecycleTest {

    /** Ghi lại mọi entity đã xử lý; gỡ các entity trong {@link #toRemove} ở lượt xử lý đầu tiên. */
    private static class RecordingSystem extends IteratingKSystem {
        final List<Long> processedIds = new ArrayList<>();
        final List<KEntity> toRemove = new ArrayList<>();
        final EcsWorld world;

        RecordingSystem(EcsWorld world) {
            super(Family.all(PositionComponent.class).get());
            this.world = world;
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            processedIds.add(entity.id());
            for (KEntity victim : toRemove) {
                world.removeEntity(victim);
            }
            toRemove.clear();
        }
    }

    /** Gỡ chính entity đang xử lý — vd. quái chết ngay trong System của nó. */
    private static final class SelfRemovingSystem extends RecordingSystem {
        SelfRemovingSystem(EcsWorld world) {
            super(world);
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            super.processEntity(entity, deltaTime);
            world.removeEntity(entity);
        }
    }

    private static KEntity spawn(EcsWorld world) {
        KEntity entity = world.createEntity();
        entity.add(new PositionComponent());
        return entity;
    }

    @Test
    void shouldProcessEveryEntityOnce_whenSystemRemovesTheEntityItIsProcessing() {
        EcsWorld world = new EcsWorld();
        SelfRemovingSystem remover = new SelfRemovingSystem(world);
        world.addSystem(remover);
        KEntity first = spawn(world);
        KEntity second = spawn(world);
        KEntity third = spawn(world);

        world.update(1f);

        assertThat(remover.processedIds).containsExactlyInAnyOrder(first.id(), second.id(), third.id());
        assertThat(world.entityCount()).isZero();
    }

    @Test
    void shouldStillProcessLastEntity_whenSystemRemovesAnotherEntityMidIteration() {
        EcsWorld world = new EcsWorld();
        RecordingSystem system = new RecordingSystem(world);
        world.addSystem(system);
        KEntity first = spawn(world);
        KEntity second = spawn(world);
        KEntity third = spawn(world);
        // Xử lý `first` thì gỡ chính nó (đã xử lý xong) — `third` không được vì thế mà mất lượt.
        system.toRemove.add(first);

        world.update(1f);

        assertThat(system.processedIds).containsExactlyInAnyOrder(first.id(), second.id(), third.id());
        assertThat(world.entityCount()).isEqualTo(2);
    }

    @Test
    void shouldHideRemovedEntityFromLaterSystems_whenRemovedDuringUpdate() {
        EcsWorld world = new EcsWorld();
        RecordingSystem killer = new RecordingSystem(world);
        RecordingSystem later = new RecordingSystem(world);
        world.addSystem(killer);
        world.addSystem(later);
        KEntity victim = spawn(world);
        killer.toRemove.add(victim);

        world.update(1f);

        assertThat(later.processedIds).isEmpty();
        assertThat(world.entityCount()).isZero();
    }

    /** Server bắt lỗi của tick rồi tick tiếp (ADR-026) — world không được kẹt ở chế độ "đang lặp". */
    @Test
    void shouldRemoveImmediatelyAgain_afterSystemThrowsMidIteration() {
        EcsWorld world = new EcsWorld();
        KEntity victim = spawn(world);
        KEntity survivor = spawn(world);
        world.addSystem(new RecordingSystem(world) {
            @Override
            protected void processEntity(KEntity entity, float deltaTime) {
                world.removeEntity(victim);
                throw new IllegalStateException("system failure");
            }
        });
        assertThatThrownBy(() -> world.update(1f)).isInstanceOf(IllegalStateException.class);
        assertThat(world.entityCount()).isEqualTo(1);

        world.removeEntity(survivor);

        assertThat(world.entityCount()).isZero();
    }

    @Test
    void shouldNotRemoveRecycledEntity_whenStaleHandleIsRemovedAgain() {
        EcsWorld world = new EcsWorld();
        RecordingSystem system = new RecordingSystem(world);
        world.addSystem(system);
        KEntity stale = spawn(world);
        world.removeEntity(stale);
        KEntity fresh = spawn(world); // PooledEngine cấp lại đúng entity gốc vừa gỡ

        world.removeEntity(stale);
        world.update(1f);

        assertThat(world.entityCount()).isEqualTo(1);
        assertThat(system.processedIds).containsExactly(fresh.id());
    }

    @Test
    void shouldNotRemoveRecycledEntity_whenHandleFromBeforeClearIsRemoved() {
        EcsWorld world = new EcsWorld();
        KEntity previousSession = spawn(world);
        world.clear();
        RecordingSystem system = new RecordingSystem(world);
        world.addSystem(system);
        KEntity nextSession = spawn(world);

        world.removeEntity(previousSession);
        world.update(1f);

        assertThat(system.processedIds).containsExactly(nextSession.id());
    }
}
