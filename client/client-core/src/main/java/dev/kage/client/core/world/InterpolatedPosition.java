package dev.kage.client.core.world;

import dev.kage.engine.ecs.ComponentMapper;
import dev.kage.engine.ecs.KEntity;
import dev.kage.engine.ecs.component.PositionComponent;

/**
 * Vị trí để VẼ một thực thể giữa hai tick cố định: {@code prev + (hiện tại − prev) × alpha}. Tách khỏi
 * {@link WorldScreen} (2026-10-17) — phép tính thuần, không thuộc màn hình, và lớp đó đã chạm trần
 * độ phức tạp PMD của cả lớp. Ba trục giống hệt nhau; trục dọc theo ADR-033.
 */
final class InterpolatedPosition {

    private static final ComponentMapper<PositionComponent> POSITION = ComponentMapper.getFor(PositionComponent.class);

    private InterpolatedPosition() {}

    static float x(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevX + ((pos.x - pos.prevX) * alpha);
    }

    static float y(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevY + ((pos.y - pos.prevY) * alpha);
    }

    static float z(KEntity entity, float alpha) {
        PositionComponent pos = POSITION.get(entity);
        return pos.prevZ + ((pos.z - pos.prevZ) * alpha);
    }
}
