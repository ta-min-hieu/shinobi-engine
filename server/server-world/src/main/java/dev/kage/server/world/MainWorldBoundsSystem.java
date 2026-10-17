package dev.kage.server.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.Family;
import dev.kage.engine.ecs.IteratingKSystem;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;
import dev.kage.server.world.component.MoveIntentComponent;

/**
 * Giữ NHÂN VẬT trong biên trục X của world chính ({@link WorldConstants#MAIN_WORLD_MIN_X} ..
 * {@link WorldConstants#MAIN_WORLD_MAX_X}) — dòng 67(c), chủ dự án duyệt 2026-10-17.
 *
 * <p>Chạy SAU mọi hệ áp chuyển động trong tick (di chuyển, trục dọc, va chạm địa hình), nên vị trí
 * phát ra trong tick đã nằm trong biên. Nhân vật được lưu ở ngoài biên từ trước (trước khi có biên)
 * bị kéo về mép ngay tick đầu tiên sau khi vào world. Chỉ kẹp vị trí, không đụng vận tốc: giữ phím
 * đi ra ngoài thì đứng lại ở mép, đổi hướng là đi tiếp được ngay.
 *
 * <p>Chỉ nhân vật (có {@link MoveIntentComponent}) — quái đã có leash về điểm spawn, NPC không đi.
 * Không cấp phát, không log (/CLAUDE.md §6).
 */
final class MainWorldBoundsSystem extends IteratingKSystem {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    MainWorldBoundsSystem() {
        super(Family.all(MoveIntentComponent.class, PositionComponent.class).get());
    }

    @Override
    protected void processEntity(KEntity entity, float deltaTime) {
        PositionComponent position = POSITION.get(entity);
        position.x = clamp(position.x);
    }

    /** {@code x} kẹp vào biên world chính. */
    static float clamp(float x) {
        return Math.clamp(x, WorldConstants.MAIN_WORLD_MIN_X, WorldConstants.MAIN_WORLD_MAX_X);
    }
}
