package dev.kage.engine.ecs;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.engine.ecs.component.VelocityComponent;
import dev.kage.engine.ecs.system.MovementSystem;

/**
 * ⚠️ PHÁT HIỆN THẬT (Phase 4): {@code Engine.addSystem} của Ashley coi 2 system CÙNG
 * {@code getClass()} là "cùng 1 system, thay thế cái cũ". Nếu {@link IteratingKSystem} bọc qua 1
 * lớp adapter DÙNG CHUNG, đăng ký system thứ 2 sẽ ÂM THẦM GỠ system thứ nhất (không lỗi, không
 * log — MovementSystem đơn giản dừng chạy). Lỗi này vô hình suốt Phase 1-3 (chỉ bao giờ có ĐÚNG 1
 * System) — test này khoá lại hành vi "≥2 System cùng lúc phải chạy cả 2", không cho tái diễn.
 */
class EcsWorldTest {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    /** System thứ 2 tối giản — chỉ cần KHÁC {@link MovementSystem} để tái hiện lỗi gốc. */
    private static final class MarkerSystem extends IteratingKSystem {
        int processedCount;

        MarkerSystem() {
            super(Family.all(PositionComponent.class).get());
        }

        @Override
        protected void processEntity(KEntity entity, float deltaTime) {
            processedCount++;
        }
    }

    @Test
    void shouldRunAllRegisteredSystems_whenMultipleSystemsShareAFamilyComponent() {
        EcsWorld world = new EcsWorld();
        MarkerSystem marker = new MarkerSystem();
        world.addSystem(new MovementSystem());
        world.addSystem(marker);

        KEntity entity = world.createEntity();
        PositionComponent position = new PositionComponent();
        entity.add(position);
        entity.add(new VelocityComponent());
        POSITION.get(entity).x = 0f;
        VelocityComponent velocity =
                ComponentMapper.getFor(VelocityComponent.class).get(entity);
        velocity.x = 10f;

        world.update(1f);

        // MovementSystem (đăng ký TRƯỚC) phải vẫn chạy — không bị MarkerSystem (đăng ký SAU) gỡ mất.
        assertThat(POSITION.get(entity).x).isEqualTo(10f);
        // MarkerSystem (đăng ký SAU) cũng phải chạy — cả 2 System cùng hoạt động, không cái nào bị loại.
        assertThat(marker.processedCount).isEqualTo(1);
    }

    /**
     * Rời hẳn một phiên chơi (bị đá vì đăng nhập nơi khác — quyết định chủ dự án 2026-09-25 — rồi về
     * màn đăng nhập): màn world kế tiếp dùng lại CÙNG {@link EcsWorld} của engine. Không dọn thì nó
     * đăng ký System di chuyển LẦN HAI (dự đoán cục bộ chạy nhanh gấp đôi) và nhân vật cũ còn nằm lại.
     */
    @Test
    void shouldForgetEveryEntityAndSystem_whenCleared() {
        EcsWorld world = new EcsWorld();
        MarkerSystem previousSession = new MarkerSystem();
        world.addSystem(previousSession);
        world.createEntity().add(new PositionComponent());

        world.clear();
        MarkerSystem nextSession = new MarkerSystem();
        world.addSystem(nextSession);
        world.createEntity().add(new PositionComponent());
        world.update(1f);

        assertThat(world.entityCount()).isEqualTo(1);
        assertThat(previousSession.processedCount).isZero();
        assertThat(nextSession.processedCount).isEqualTo(1);
    }
}
